package customexport.mii.greifswald

import de.kairos.fhir.centraxx.metamodel.IdContainer
import de.kairos.fhir.centraxx.metamodel.IdContainerType
import org.apache.commons.codec.digest.DigestUtils
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Encounter.EncounterLocationStatus

import static de.kairos.fhir.centraxx.metamodel.RootEntities.patientTransfer

final String fhir_id_prefix = "cxx-ppil-prod-"

encounter {
  final String idHash = DigestUtils.md2Hex(context.source[patientTransfer().id()] as String)

  id = "Encounter/PT-" + fhir_id_prefix + idHash

  meta {
    profile("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung|2026.0.0")
  }

  status = Encounter.EncounterStatus.UNKNOWN


  type {
    coding {
      system ="http://fhir.de/CodeSystem/Kontaktebene"
      code = "versorgungsstellenkontakt"
    }
  }

  class_ {
    system = "http://terminology.hl7.org/CodeSystem/v3-ActCode"
    code = "IMP"
  }

  if (context.source[patientTransfer().transferDate()]){
    period {
      start = context.source[patientTransfer().transferDate()]
    }
  }

  if (context.source[patientTransfer().bed()] != null) {
    location {
      //TODO map code to location.location.display or location.location.identifier?
      location {
        display = context.source[patientTransfer().bed().code()]
      }
      status = EncounterLocationStatus.ACTIVE
      physicalType {
        coding {
          system = "http://terminology.hl7.org/CodeSystem/location-physical-type"
          code = "be"
          display = "bed"
        }
      }
    }
  }

  if (context.source[patientTransfer().room()] != null) {
    location {
      location {
        display = context.source[patientTransfer().room().code()]
      }
      status = EncounterLocationStatus.ACTIVE
      physicalType {
        coding {
          system = "http://terminology.hl7.org/CodeSystem/location-physical-type"
          code = "ro"
          display = "room"
        }
      }
    }
  }

  subject {
    final def patientIdContainerPSN = context.source[patientTransfer().episode().patientContainer().idContainer()].find {
      "TEST_ID" == it[IdContainer.ID_CONTAINER_TYPE]?.getAt(IdContainerType.CODE)
    }

    final String patientHash = DigestUtils.md5Hex(patientIdContainerPSN[IdContainer.PSN] as String)

    reference = 'Patient/' + fhir_id_prefix + patientHash

    // Achtung: Muss für fhir-to-i2b2 der selbe sein, wie der Patient.identifier im Template patient.groovy
    identifier {
      system = "https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/patientOld-identifier"
      value = fhir_id_prefix + patientHash
    }
  }

  if (context.source[patientTransfer().medDepartment()] != null) {
    location {
      location {
        display = context.source[patientTransfer().medDepartment().code()]
      }
      status = EncounterLocationStatus.ACTIVE
      physicalType {
        coding {
          system = "http://terminology.hl7.org/CodeSystem/location-physical-type"
          code = "wa"
          display = "ward"
        }
      }
    }
  }

  if (context.source[patientTransfer().habitation()]){
    serviceProvider {
      reference = 'Organization/' + fhir_id_prefix + DigestUtils.md5Hex(context.source[patientTransfer().habitation().id()] as String)
    }
  }

  if (context.source[patientTransfer().episode()]){
    partOf {
      reference = "Encounter/" + fhir_id_prefix + DigestUtils.md5Hex(context.source[patientTransfer().episode().id()] as String)
    }
  }
}