package projects.mii_bielefeld

import common.AbstractExportScriptTest
import common.ExportScriptTest
import common.TestResources
import de.kairos.fhir.centraxx.metamodel.IdContainer
import de.kairos.fhir.centraxx.metamodel.IdContainerType
import de.kairos.fhir.dsl.r4.context.Context
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Identifier
import org.junit.jupiter.api.Assumptions

import static de.kairos.fhir.centraxx.metamodel.RootEntities.episode
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

@TestResources(
    groovyScriptPath = "src/main/groovy/projects/mii_bielefeld/encounter.groovy",
    contextMapsPath = "src/test/resources/projects/mii_bielefeld/encounter.json"
)
class EpisodeExportScriptTest extends AbstractExportScriptTest<Encounter> {

  @ExportScriptTest
  void testThatIdentifiersAreSet(final Context context, final Encounter resource) {

    Assumptions.assumeTrue((context.source[episode().idContainer()] as List).size() > 0)

    assertTrue(resource.hasIdentifier())
    assertEquals((context.source[episode().idContainer()] as List).size(), resource.getIdentifier().size())

    resource.getIdentifier()
        .collect { it.getType() }
        .each {
          assertNotNull(it)
          assertTrue(it.hasCoding("http://terminology.hl7.org/CodeSystem/v2-0203", "VN"))
        }


    resource.getIdentifier().each { final Identifier fhirIdentifier ->
      final def idc = context.source[episode().idContainer()].find {
        it[IdContainer.ID_CONTAINER_TYPE][IdContainerType.CODE].equals(fhirIdentifier.getSystem())
      }

      assertNotNull(idc)
      assertEquals(idc[IdContainer.PSN], fhirIdentifier.getValue())
    }
  }

  @ExportScriptTest
  void testThatClassIsSet(final Context context, final Encounter resource) {
    Assumptions.assumeTrue(context.source[episode().stayType()] != null)

    assertTrue(resource.hasClass_())
    assertEquals("http://terminology.hl7.org/CodeSystem/v3-ActCode", resource.getClass_().getSystem())
    assertEquals(context.source[episode().stayType().code()], resource.getClass_().getCode())
  }

  @ExportScriptTest
  void testThatSubjectIsSet(final Context context, final Encounter resource) {
    assertTrue(resource.hasSubject())
    assertEquals("Patient/" + context.source[episode().patientContainer().id()], resource.getSubject().getReference())
  }

  @ExportScriptTest
  void testThatPeriodIsSet(final Context context, final Encounter resource) {

    Assumptions.assumeTrue(context.source[episode().validFrom()] || context.source[episode().validUntil()])

    assertTrue(resource.hasPeriod())


    Assumptions.assumingThat(context.source[episode().validFrom()] != null,
        { ->
          assertEquals(new DateTimeType(context.source[episode().validFrom()] as String).getValue(),
              resource.getPeriod().getStart())
        }
    )

    Assumptions.assumingThat(context.source[episode().validUntil()] != null,
        { ->
          assertEquals(new DateTimeType(context.source[episode().validUntil()] as String).getValue(),
              resource.getPeriod().getEnd())
        }
    )
  }
}