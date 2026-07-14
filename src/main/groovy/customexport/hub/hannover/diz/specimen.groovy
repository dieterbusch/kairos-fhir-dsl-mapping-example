package customexport.hub.hannover.diz


import de.kairos.fhir.centraxx.metamodel.IdContainerType
import de.kairos.fhir.centraxx.metamodel.PrecisionDate
import de.kairos.fhir.centraxx.metamodel.enums.SampleKind

import static de.kairos.fhir.centraxx.metamodel.AbstractIdContainer.ID_CONTAINER_TYPE
import static de.kairos.fhir.centraxx.metamodel.AbstractIdContainer.PSN
import static de.kairos.fhir.centraxx.metamodel.RootEntities.abstractSample
import static de.kairos.fhir.centraxx.metamodel.RootEntities.patientMasterDataAnonymous
import static de.kairos.fhir.centraxx.metamodel.RootEntities.sample

/**
 * Represented by a CXX SAMPLE with HUB-Basic-Dataset for export to MHH communication server & dic
 * Refers to https://www.medizininformatik-initiative.de/Kerndatensatz/Modul_Biobank/SpecimenBioprobe.html
 *
 * @author Jonas Küttner, Markus Kersting
 * @since KAIROS-FHIR-DSL.v.1.8.0, CXX.v.3.18.1
 */

specimen {

  // fhir-id/resource-id
  id = "Specimen/" + context.source[sample().id()]

  meta {
    profile "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/ProfileSpecimenBioprobe"
  }

  // extension.diagnose: optional: aktuell nicht implementiert an HUB, da nicht Verknüpfung in der Regel nicht vorhanden

  // reference to hub-project as orga //// extension.gehoertZu
  if (context.source[sample().organisationUnit()]) {
    extension {
      url = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/VerwaltendeOrganisation"
      valueReference {
        reference = "Organization/" + context.source[sample().organisationUnit().id()]
      }
    }
  }

  // cxx-datenbank-oid bzw. ressourcen-id als 1. proben-id
  identifier {
    type {
      coding {
        system = "urn:centraxx"
        code = "CXOID"
        display = "CentraXX-Sample-OID"
      }
    }
    value = context.source[sample().id()]
  }

  // identifier: sample id, all ids are exported but only SAMPLEID is relevant for hub
  context.source[sample().idContainer()].each { final def idObj ->
    identifier {
      type {
        coding {
          system = "urn:centraxx"
          code = idObj[ID_CONTAINER_TYPE][IdContainerType.CODE] as String
          display = idObj[ID_CONTAINER_TYPE][IdContainerType.NAME] as String
        }
      }
      value = idObj[PSN]
    }
  }

  // accessionIdentifier: optional: refers best to receivce number in hub-cxx

  // sample status: unavailable when amount=0 (later: or no location or specific hub-sample-state)
  status = context.source[sample().restAmount().amount()] > 0 ? "available" : "unavailable"

  // sampletype: Mapping der hub-sampletype to snomed
  // change mapping from type+container=snomed-code
  if (context.source[sample().sampleType()]) {
    type {
      final Map<String, String> sampleTypeMap = mapSampleType(context.source[sample().sampleType().code()] as String)
      if (sampleTypeMap) {
        coding {
          system = "http://snomed.info/sct"
          //version: optional
          code = sampleTypeMap.code
          display = sampleTypeMap.display
          //userSelected: optional
        }
      } else {
        coding {
          system = "http://snomed.info/sct"
          //version: optional
          code = "119324002"
          //userSelected: optional
          //display = "Specimen of unknown material (Code:" + context.source[sample().sampleType().code()] + ")"
          display = "Specimen of unknown material (Code)"
          // text: optional: in addition to snomed code we put kind, primary container and sample type from cxx here, which defines a sample type in hub
          text = "HUB sample type: " + context.source[abstractSample().sampleType().kind()] + "/" + context.source[sample().sprecPrimarySampleContainer().code()] + "/" + context.source[sample().sampleType().code()]
        }
      }
    }
  }

  // patient reference to i-zahl
  final def PatIdContainer = context.source[patientMasterDataAnonymous().patientContainer().idContainer()]?.find {
    "MPI" == it[ID_CONTAINER_TYPE]?.getAt(IdContainerType.CODE)
  }

  subject {
    //reference = "Patient/" + context.source[sample().patientContainer().id()]
    reference = "Patient/" + PatIdContainer[PSN]
  }

  // receivedate
  receivedTime {
    date = context.source[sample().receiptDate()]?.getAt(PrecisionDate.DATE)
  }

  // mastersample
  if (context.source[sample().parent()]) {
    parent {
      reference = "Specimen/" + context.source[sample().parent().id()]
    }
  }

  // request: optional: not implemented/needed

  // ucum translation of units
  final def ucum = context.conceptMaps.builtin("centraxx_ucum")

  // sample collection infos
  collection {
    // extension: fastingStatus[x]: optional: later maybe from hub-labfinding
    // extension: Einstellung Blutversorgung: optional: https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/EinstellungBlutversorgung
    // collector: optional: not applicable
    collectedDateTime = context.source[sample().samplingDate().date()]
    // duration: optional: not applicable
    // quantity: =InitialAmount?!

    if (context.source[sample().initialAmount()]) {
      quantity {
        value = context.source[sample().initialAmount().amount()] as Number
        //unit = context.source[sample().initialAmount().unit()]
        //system = "urn:centraxx"
        unit = ucum.translate(context.source[sample().initialAmount().unit()] as String)?.code
        system = "http://unitsofmeasure.org"
      }
    }

    //bodySite: optional: not needed/known in most cases
    //fastingStatus[x]: optional: collected sometimes in hub-basic-profile
  }

  // sample reposition, when in store
  if (context.source[sample().sampleLocation()]) {
    processing {
      // extension:temperaturbedingungen: todo: when Lagerort = Tank then -190°C, when Truhe -80°C else ?!?
      extension {
        valueRange {
          low {
            system = "http://unitsofmeasure.org"
            code = "Cel"
            unit = "°C"
            value = mapLocTemp(context.source[sample().sampleLocation().locationPath()] as String)
          }
          high {
            system = "http://unitsofmeasure.org"
            code = "Cel"
            unit = "°C"
            value = mapLocTemp(context.source[sample().sampleLocation().locationPath()] as String)
          }
        }
        url = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Temperaturbedingungen"
      }
      // lagerung
      procedure {
        coding {
          system = "http://snomed.info/sct"
          code = "1186936003"
          display = "Storage of specimen (procedure)"
        }
      } // datum der einlagerung
      timeDateTime = context.source[sample().repositionDate().date()]
      // todo:optional procedure for aliqotierung, zentrifugation, einfrieren
    }
  }

  // sample container/tube
  if (context.source[sample().receptable()]) {

    container {
      // hub-tube tyoe as describtion
      description = "HUB tube type: " + context.source["receptable.sprecCode"]
      // map tube srepc type to snomed
      type {
        final Map<String, String> sampleContainerMap = mapContainer(context.source["receptable.sprecCode"] as String, context.source[sample().sampleKind()] as SampleKind)
        if (sampleContainerMap) {
          coding {
            system = "http://snomed.info/sct"
            code = sampleContainerMap.code
            display = sampleContainerMap.display
          }
        } else {
          coding {
            system = "https://doi.org/10.1089/bio.2017.0109/long-term-storage"
            code = context.source["receptable.sprecCode"]
          }
        }
      }

      // tube capacity
      capacity {
        value = context.source[sample().receptable().size()]
        unit = ucum.translate(context.source[sample().restAmount().unit()] as String)?.code
        system = "http://unitsofmeasure.org"
      }

      // amount rest
      specimenQuantity {
        value = context.source[sample().restAmount().amount()] as Number
        unit = ucum.translate(context.source[sample().restAmount().unit()] as String)?.code
        system = "http://unitsofmeasure.org"
      }

      /*
      // additive reference
      // todo: mapping from tube and container
      additiveReference {
        additiveCodeableConcept {
          coding {
            system = "http://snomed.info/sct"
            code = "105590001"
            display : "Substance (substance)"
          }
        }
      }
      */

    }
  }
}

// maps hub-cxx-sample type to snomed
static Map<String, String> mapSampleType(final String sCode) {
  switch (sCode) {
    case "AMN": return ["code": "119373006", "display": "Amniotic fluid specimen (specimen)"]
    case "ASC": return ["code": "309201001", "display": "Ascitic fluid sample (specimen)"]
    case "BAL": return ["code": "258607008", "display": "Bronchoalveolar lavage fluid sample (specimen)"]
    case "BAL(cdm)": return ["code": "258607008", "display": "Bronchoalveolar lavage fluid sample (specimen)"]
    case "BAL(ciq)": return ["code": "258607008", "display": "Bronchoalveolar lavage fluid sample (specimen)"]
    case "BAL(fac)": return ["code": "258607008", "display": "Bronchoalveolar lavage fluid sample (specimen)"]
    case "BAL(flr)": return ["code": "258587000", "display": "Buffy coat (specimen)"]
    case "BAL(fwc)": return ["code": "420135007", "display": "Whole blood (substance)"]
    case "BAL(ncs)": return ["code": "420135007", "display": "Whole blood (substance)"]
    case "BFF": return ["code": "258587000", "display": "Buffy coat (specimen)"]
    case "BLD": return ["code": "258580003", "display": "Blut, Vollblut [BLD]"]
    case "BMA": return ["code": "396997002", "display": "Specimen from bone marrow obtained by aspiration (specimen)"]
    case "BMA(dna)": return ["code": "396997002", "display": "Specimen from bone marrow obtained by aspiration (specimen)"]
    case "BMA(stc)": return ["code": "396997002", "display": "Specimen from bone marrow obtained by aspiration (specimen)"]
    case "BMK": return ["code": "446676001", "display": "Expressed breast milk specimen (specimen)"]
    case "BON": return ["code": "430268003", "display": "Specimen from bone (specimen)"]
    case "BUF": return ["code": "258587000", "display": "Buffy coat (specimen)"]
      //case "CEN(tis)": return ["code" : "?","display": "?"]
      //case "CLN": return ["code" : "?","display": "?"]
    case "CEL": return ["code": "404798000", "display": "Peripheral blood mononuclear cell (cell)"]
    case "CRD": return ["code": "122556008", "display": "Cord blood specimen (specimen)"]
    case "CSF": return ["code": "258450006", "display": "Cerebrospinal fluid sample (specimen)"]
    case "CSF(sed)": return ["code": "258450006", "display": "Cerebrospinal fluid sample (specimen)"]
    case "DWB": return ["code": "119294007", "display": "Hair specimen (specimen)"]
    case "HAR": return ["code": "119326000", "display": "Dried blood specimen (specimen)"]
    case "NAL": return ["code": "119327009", "display": "Nail specimen (specimen)"]
    case "NAS": return ["code": "258467004", "display": "Nasopharyngeal washings (specimen)"]
      //case "PEL": return ["code" : "?","display": "?"]
      //case "PEN(tis)": return ["code" : "?","display": "?"]
    case "PFL": return ["code": "418564007", "display": "Pleural fluid specimen (specimen)"]
    case "PL1": return ["code": "119361006", "display": "Plasma specimen (specimen)"]
    case "PL2": return ["code": "119361006", "display": "Plasma specimen (specimen)"]
    case "PLC": return ["code": "119403008", "display": "Specimen from placenta (specimen)"]
    case "RBC": return ["code": "119351004", "display": "Erythrocyte specimen (specimen)"]
    case "SAL": return ["code": "119342007", "display": "Saliva specimen (specimen)"]
    case "SEM": return ["code": "119347001", "display": "Seminal fluid specimen (specimen)"]
    case "SER": return ["code": "119364003", "display": "Serum specimen (specimen)",]
    case "SPT": return ["code": "119334006", "display": "Sputum specimen (specimen)"]
    case "SPT(cel)": return ["code": "119334006", "display": "Sputum specimen (specimen)"]
    case "STL": return ["code": "119339001", "display": "Stool specimen (specimen)"]
    case "STL(bac)": return ["code": "119339001", "display": "Stool specimen (specimen)"]
    case "SYN": return ["code": "119332005", "display": "Synovial fluid specimen (specimen)"]
      //case "PEN(tis)": return ["code" : "?","display": "?"]
    case "TCM": return ["code": "119376003", "display": "Tissue specimen (specimen)"]
    case "TER": return ["code": "122594008", "display": "Tears specimen (specimen)"]
    case "TIS": return ["code": "119376003", "display": "Tissue specimen (specimen)"]
    case "TIS(pls)": return ["code": "119376003", "display": "Tissue specimen (specimen)"]
    case "TIS(tum)": return ["code": "119376003", "display": "Tissue specimen (specimen)"]
    case "TTH": return ["code": "430319000", "display": "Specimen from tooth (specimen)"]
    case "U24": return ["code": "276833005", "display": "24 hour urine sample (specimen)"]
    case "URM": return ["code": "122575003", "display": "Urine specimen (specimen)"]
    case "URN": return ["code": "278020009", "display": "Spot urine sample (specimen)"]
    case "URT": return ["code": "409821005", "display": "Timed urine specimen (specimen)"]
    case "ZZZ(pbm)": return ["code": "404798000", "display": "Peripheral blood mononuclear cell (cell)"]
      //case "ZZZ(bac)": return ["code" : "?","display": "?"]
    case "ZZZ(brs)": return ["code": "119391001", "display": "Specimen from bronchus (specimen)"]
    case "ZZZ(bsc)": return ["code": "415293009", "display": "Respiratory secretion (specimen)"]
      //case "ZZZ(bwp)": return ["code" : "?","display": "?"]
      //case "ZZZ(cd4)": return ["code" : "?","display": "?"]
    case "ZZZ(cfd)": return ["code": "726740008", "display": "Cell free deoxyribonucleic acid (substance)"]
      //case "ZZZ(csl)": return ["code" : "?","display": "?"]
      //case "ZZZ(cso)": return ["code" : "?","display": "?"]
    case "ZZZ(cus)": return ["code": "702451000", "display": "Cultured cells (substance)"]
    case "ZZZ(dna)": return ["code": "258566005", "display": "Deoxyribonucleic acid specimen (specimen)"]
    case "ZZZ(mon)": return ["code": "55918008", "display": "Monocyte (cell)"]
      //case "ZZZ(mor)": return ["code" : "?","display": "?"]
      //case "ZZZ(nab)": return ["code" : "?","display": "?"]
      //case "ZZZ(nai)": return ["code" : "?","display": "?"]
    case "ZZZ(nas)": return ["code": "447339001", "display": "Nasal smear specimen (specimen)"]
    case "ZZZ(pbm)": return ["code": "404798000", "display": "Peripheral blood mononuclear cell (cell)"]
    case "ZZZ(pbu)": return ["code": "404798000", "display": "Peripheral blood mononuclear cell (cell)"]
      //case "ZZZ(pld)": return ["code" : "?","display": "?"]
    case "ZZZ(rna)": return ["code": "27888000", "display": "Ribonucleic acid (substance)"]
      //case "ZZZ(sdn)": return ["code" : "?","display": "?"]
      //case "ZZZ(tdn)": return ["code" : "?","display": "?"]
    case "ZZZ(trw)": return ["code": "258469001", "display": "Pharyngeal washings (specimen)"]
      //case "ZZZ(tsc)": return ["code" : "?","display": "?"]
      //case "ZZZ(unk)": return ["code" : "?","display": "?"]  // unbekannt
    case "ZZZ(usd)": return ["code": "122567009", "display": "Urine sediment specimen (specimen)"]
    default: return null
  }
}

// maps the sprec code of cxx-tubes to snomed
static mapContainer(final String sprecCode, final SampleKind sampleKind) {
  switch (sprecCode) {
    case ["A", "B", "V", "J", "K", "S", "T", "W"]: return ["code"   : "34234003:840560000=256633009",
                                                           "display": "Plastic tube , device (physical object): Has compositional material=Polypropylene (substance)"]
    case ["C", "D", "E", "N", "Y"]: return ["code"   : "83059008",
                                            "display": "Tube, device (physical object)"]
    case ["F", "G", "H", "I", "O"] && sampleKind == SampleKind.TISSUE: return ["code"   : "464601003",
                                                                               "display": "Tissue storage straw (physical object)"]
    case ["L", "M"]: return ["code"   : "434822004",
                             "display": "Specimen well (physical object)"]
    case ["Q"]: return ["code"   : "463490008",
                        "display": "Medical bag (physical object)"]
    case ["Z"]: return ["code"   : "706437002",
                        "display": "Container (physical object)"]
    default: null
  }
}

// maps hub-cxx-locationpath to storage temp
static String mapLocTemp(final String locationPath) {
  if (locationPath.contains("Tank")) return "-196" else return "-80"
}

