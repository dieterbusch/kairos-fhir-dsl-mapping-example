package projects.mii_bielefeld

import groovy.xml.MarkupBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertTrue

class FlexibleValueRefsTest {

    @Test
    void generateFlexibleValueRefsXml() {
        // Load CSV file
        def csvFile = new File(getClass().getClassLoader().getResource('bevor_MII_LOINC_Top300_Stand-2018-08-08.csv').toURI())
        assertTrue(csvFile.exists(), "CSV file does not exist")

        // Parse CSV data
        def csvData = csvFile.readLines().drop(1).collect { line ->
            def fields = line.split(';')
            [
                    COMPONENT : fields[0],
                    PROPERTY  : fields[1],
                    TIME_ASPCT: fields[2],
                    SYSTEM    : fields[3],
                    SCALE_TYP : fields[4],
                    METHOD_TYP: fields.size() > 5 ? fields[5] : 'null'
            ]
        }


        // Generate XML structure
        def outputXmlFile = new File('generated_output.xml')
        outputXmlFile.withWriter('UTF-8') { writer ->
            def xml = new MarkupBuilder(writer)
            xml.CentraXXDataExchange('xmlns': 'http://www.kairos-med.de',
                    'xmlns:xsi': 'http://www.kairos-med.de ../CentraXXExchange.xsd') {
                Source("CENTRAXX")
                SourceVersion("2024.5.0-SNAPSHOT")
                ExportDate("2024-11-05T14:59:01.798+01:00")
                CatalogueData {
                    FlexibleDataSetCatalogueItem(assignStrict: "true") {
                        Code("MP_DiagnosticReportLab")
                        NameMultilingualEntries {
                            Lang("de")
                            Value("MP_DiagnosticReportLab")
                        }
                        NameMultilingualEntries {
                            Lang("en")
                            Value("(en) - MP_DiagnosticReportLab")
                        }
                        csvData.each { row ->
                            FlexibleValueComplexRefs {
                                FlexibleValueRef("${row.COMPONENT}:${row.PROPERTY}:${row.TIME_ASPCT}:${row.SYSTEM}:${row.SCALE_TYP}:${row.METHOD_TYP}")
                                Required('false')
                            }
                        }
                        Systemwide("true")
                        FlexibleDataSetType("MEASUREMENT")
                        Category("LABOR")
                        CrfTemplateRef(RefUrl: "/rest/export/catalogdata/CrfTemplate/20601") {
                            Name("MP_DiagnosticReportLab")
                            Version("0")
                            SyncId("07c09165-d0e7-4cf8-a9a2-ec7e74dfdb4f")
                        }
                        Version("1")
                        SyncId("74f1b4cb-dfeb-4721-babc-6f5ff880f43a")
                    }
                }
            }
        }
        // Assert that the file is created
        assertTrue(outputXmlFile.exists(), "Output XML file was not created")
        println "XML file generated at: ${outputXmlFile.absolutePath}"
    }
}

