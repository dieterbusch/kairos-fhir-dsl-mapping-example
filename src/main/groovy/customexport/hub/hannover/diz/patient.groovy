package customexport.hub.hannover.diz

import ca.uhn.fhir.model.api.TemporalPrecisionEnum
import de.kairos.centraxx.fhir.r4.utils.FhirUrls
import de.kairos.fhir.centraxx.metamodel.IdContainer
import de.kairos.fhir.centraxx.metamodel.IdContainerType

import static de.kairos.fhir.centraxx.metamodel.RootEntities.patient

/*
 * export patient from hub-hdrp for mhh-diz
 */
patient {

  final def idContainer = context.source[patient().patientContainer().idContainer()].find {
    "MPI" == it[IdContainer.ID_CONTAINER_TYPE]?.getAt(IdContainerType.CODE)
  }

  id = "Patient/" + idContainer[IdContainer.PSN]

  // CXX-FHIR-OID als 2. Identifier
  if (idContainer) {
    identifier {
      value = context.source[patient().patientContainer().id()] // cxx oid
      type {
        coding {
          system = "urn:centraxx"
          code = "CentraXX-Patient-ID"
        }
      }
    }
  }

  //gender
  gender {
    value = toGender(context.source[patient().genderType()])
    if (value.toString() == "other") {
      extension {
        url = "http://fhir.de/StructureDefinition/gender-amtlich-de"
        valueCoding {
          system = "http://fhir.de/CodeSystem/gender-amtlich-de"
          code = "D"
          display = "divers"
        }
      }
    }
  }

  //dob (year only!)
  if (context.source[patient().birthdate()]) {
    birthDate {
      if ("UNKNOWN" == context.source[patient().birthdate().precision()]) {
        extension {
          url = FhirUrls.Extension.FhirDefaults.DATA_ABSENT_REASON
          valueCode = "unknown"
        }
      } else {
        date = context.source[patient().birthdate().date()]
        precision = TemporalPrecisionEnum.YEAR.name()
      }
    }
  }

} // patient

// gender mapping
static def toGender(final Object cxx) {
  switch (cxx) {
    case 'MALE':
      return "male"
    case 'FEMALE':
      return "female"
    case 'UNKNOWN':
      return "unknown"
    default:
      return "other"
  }
}
