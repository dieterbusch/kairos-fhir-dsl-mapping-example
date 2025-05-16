package projects.patientfinder.hull

import ca.uhn.fhir.model.api.TemporalPrecisionEnum
import de.kairos.centraxx.fhir.r4.utils.FhirUrls
import de.kairos.fhir.centraxx.metamodel.CrfTemplateField
import de.kairos.fhir.centraxx.metamodel.Episode
import de.kairos.fhir.centraxx.metamodel.LaborFinding
import de.kairos.fhir.centraxx.metamodel.LaborFindingLaborValue
import de.kairos.fhir.centraxx.metamodel.LaborMapping
import de.kairos.fhir.centraxx.metamodel.LaborMethod
import de.kairos.fhir.centraxx.metamodel.LaborValue
import de.kairos.fhir.centraxx.metamodel.Multilingual
import de.kairos.fhir.centraxx.metamodel.OrganisationUnit
import de.kairos.fhir.centraxx.metamodel.PatientTransfer
import de.kairos.fhir.centraxx.metamodel.ValueReference
import de.kairos.fhir.centraxx.metamodel.enums.EpisodeStatus
import org.hl7.fhir.r4.model.CatalogEntry
import org.hl7.fhir.r4.model.Encounter

import static de.kairos.fhir.centraxx.metamodel.AbstractCode.CODE
import static de.kairos.fhir.centraxx.metamodel.AbstractCodeSyncIdMultilingual.MULTILINGUALS
import static de.kairos.fhir.centraxx.metamodel.AbstractIdContainer.ID_CONTAINER_TYPE
import static de.kairos.fhir.centraxx.metamodel.AbstractIdContainer.PSN
import static de.kairos.fhir.centraxx.metamodel.IdContainerType.DECISIVE
import static de.kairos.fhir.centraxx.metamodel.RootEntities.episode
import static de.kairos.fhir.centraxx.metamodel.RootEntities.medication

/**
 * Represents a CXX Episode.
 * Specified by https://www.hl7.org/fhir/us/core/StructureDefinition-us-core-encounter.html
 *
 * hints:
 * - Mapping uses SNOMED-CT concepts.
 * - There is no participant, reasonCode/reference, hospitalization, location in CXX
 *
 *
 * @author Mike Wähnert
 * @since v.1.13.0, CXX.v.2023.3.0
 */

final String TREATMENT_SERVICE = "treatmentService"
final String TYPE = "type"
final Map PROFILE_TYPES = [
    (TREATMENT_SERVICE): LaborFindingLaborValue.MULTI_VALUE_REFERENCES,
    (TYPE)             : LaborFindingLaborValue.CATALOG_ENTRY_VALUE
]

encounter {

  if (isFakeEpisode(context.source)) {
    return //filters Encounters with EncounterOID prefix FAKE_
  }

  id = "Encounter/" + context.source[episode().id()]

  final def mapping = context.source[medication().laborMappings()].find { final def lm ->
    lm[LaborMapping.LABOR_FINDING][LaborFinding.LABOR_METHOD][CODE] == "Encounter_profile"
  }

  final Map<String, Object> lflvMap = getLflvMap(mapping, PROFILE_TYPES)

  meta {
    profile "http://hl7.org/fhir/us/core/StructureDefinition/us-core-encounter"
  }

  context.source[episode().idContainer()].each { final idContainer ->
    final boolean isDecisive = idContainer[ID_CONTAINER_TYPE]?.getAt(DECISIVE)
    if (isDecisive) {
      identifier {
        value = idContainer[PSN]
        type {
          coding {
            system = FhirUrls.System.IdContainerType.BASE_URL
            code = idContainer[ID_CONTAINER_TYPE]?.getAt(CODE)
          }
        }
      }
    }
  }

  status = mapStatus(context.source[episode().status()] as String)

  if (context.source[episode().stayType().code()]) {
    class_ {
      system = FhirUrls.System.Episode.StayType.BASE_URL
      code = context.source[episode().stayType().code()]
      display = context.source[episode().stayType().multilinguals()].find { final def ml ->
        ml[Multilingual.SHORT_NAME] != null && ml[Multilingual.LANGUAGE] == "en"
      }?.getAt(Multilingual.SHORT_NAME)
    }
  }

  type {
    coding {
      system = "http://snomed.info/sct"
      code = "308335008"
      display = "Patient encounter procedure"
    }
  }

  subject {
    reference = "Patient/" + context.source[episode().patientContainer().id()]
  }

  if (context.source[episode().parent()]) {
    partOf {
      reference = "Episode/" + context.source[episode().parent().id()]
    }
  }

  period {
    if (context.source[episode().validFrom()]) {
      start {
        date = context.source[episode().validFrom()]
        precision = TemporalPrecisionEnum.DAY.toString()
      }
    }

    if (context.source[episode().validUntil()]) {
      end {
        date = context.source[episode().validUntil()]
        precision = TemporalPrecisionEnum.DAY.toString()
      }
    }
  }

  if (context.source[episode().habitation()]) {
    serviceProvider {
      reference = "Organization/" + context.source[episode().habitation().id()]
    }
  }

  for (final pt in context.source[episode().patientTransfers()]) {
    location {
      location {
        reference = "Location/PT-" + pt[PatientTransfer.ID]
      }
    }
  }

  if (context.source[episode().attendingDoctor()]) {
    participant {
      individual {
        reference = "Practitioner/" + context.source[episode().attendingDoctor().id()]
      }
    }
  }

  if (lflvMap.containsKey(TREATMENT_SERVICE)) {
    lflvMap.get(TREATMENT_SERVICE).each { final def valueRef ->
      if (valueRef && valueRef[ValueReference.ORGANIZATION_VALUE]) {
        serviceProvider {
          reference = "Organization/" + valueRef[ValueReference.ORGANIZATION_VALUE][OrganisationUnit.ID]
        }
      }
    }
  }

  if (lflvMap.containsKey(TYPE)) {
    type {
      lflvMap.get(TYPE).each { final def entry ->
        coding {
          code = entry[CODE] as String
          display = entry[MULTILINGUALS].find { final def ml ->
            ml[Multilingual.SHORT_NAME] != null && ml[Multilingual.LANGUAGE] == "en"
          }?.getAt(Multilingual.SHORT_NAME)
        }
      }
    }
  }
}

static boolean isFakeEpisode(final def episode) {
  if (episode == null) {
    return true
  }

  if (["SACT", "COSD"].contains(episode[Episode.ENTITY_SOURCE])) {
    return true
  }

  final def fakeId = episode[Episode.ID_CONTAINER]?.find { (it[PSN] as String).toUpperCase().startsWith("FAKE") }
  return fakeId != null
}

static Map<String, Object> getLflvMap(final def mapping, final Map<String, String> types) {
  final Map<String, Object> lflvMap = [:]
  if (!mapping) {
    return lflvMap
  }

  types.each { final String lvCode, final String lvType ->
    final def lflvForLv = mapping[LaborMapping.LABOR_FINDING][LaborFinding.LABOR_FINDING_LABOR_VALUES].find { final def lflv ->
      lflv[LaborFindingLaborValue.CRF_TEMPLATE_FIELD][CrfTemplateField.LABOR_VALUE][CODE] == lvCode
    }

    if (lflvForLv && lflvForLv[lvType]) {
      lflvMap[(lvCode)] = lflvForLv[lvType]
    }
  }
  return lflvMap
}

static Encounter.EncounterStatus mapStatus(final String cxxStatus) {
  switch (cxxStatus) {
    case EpisodeStatus.FINISHED.toString():
      return Encounter.EncounterStatus.FINISHED
    case EpisodeStatus.ARRIVED.toString():
      return Encounter.EncounterStatus.ARRIVED
    case EpisodeStatus.CANCELED.toString():
      return Encounter.EncounterStatus.CANCELLED
    case EpisodeStatus.TRIAGED.toString():
      return Encounter.EncounterStatus.TRIAGED
    case EpisodeStatus.PLANNED.toString():
      return Encounter.EncounterStatus.PLANNED
    case EpisodeStatus.IN_PROGRESS.toString():
      return Encounter.EncounterStatus.INPROGRESS

    default:
      return Encounter.EncounterStatus.UNKNOWN
  }
}