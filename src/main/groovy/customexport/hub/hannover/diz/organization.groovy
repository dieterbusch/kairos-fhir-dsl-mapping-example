package customexport.hub.hannover.diz

import de.kairos.fhir.centraxx.metamodel.Multilingual

import static de.kairos.fhir.centraxx.metamodel.RootEntities.organizationUnit

/**
 * Represented by a HDRP OrganizationUnit
 * @author Jonas Küttner
 * @since KAIROS-FHIR-DSL.v.1.52.0, HDRP.v.2025.3.0
 * 25.11.2025: kersting: hub cutstomization
 */

organization {

  // no mhh clinics, only hub-projects > all research samples refer to one hub-project
  if (!(context.source[organizationUnit().code()] as String).startsWith("P-2")) {
    return
  }

  // fhir-id aus db
  id = "Organization/" + context.source[organizationUnit().id()]

  // contact
  contact {
    purpose {
      coding {
        code = "RESEARCH"
        system = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/CodeSystem/ContactType"
      }
    }
    extension {
      url = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/KontaktRolle"
      valueString = "Biobank"
    }
    telecom {
      system = "email"
      value = "HUB.Projekte@mh-hannover.de"
    }
    address {
      line = ["Feodor-Lynen-Str. 15", "CRC Hannover"]
      postalCode = "30625"
      city = "Hannover"
    }
  }
  extension {
    url = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/BeschreibungSammlung"
    valueMarkdown = "sample collection @ hannover unified biobank (HUB)"
  }

  // CXX-Code of OU as ID
  identifier {
    system = "urn:centraxx:org"
    value = context.source[organizationUnit().code()]
  }

  // name // german
  name = context.source[organizationUnit().multilinguals()].find { final def ml ->
    ml[Multilingual.LANGUAGE] == "de" && ml[Multilingual.SHORT_NAME] != null
  }?.getAt(Multilingual.SHORT_NAME) as String

}

