package common

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport
import ca.uhn.fhir.validation.FhirValidator
import ca.uhn.fhir.validation.ResultSeverityEnum
import ca.uhn.fhir.validation.ValidationResult
import de.kairos.fhir.dsl.r4.context.Context
import de.kairos.fhir.dsl.r4.execution.Fhir4ScriptEngine
import de.kairos.fhir.dsl.r4.execution.Fhir4ScriptRunner
import groovy.json.JsonSlurper
import org.hl7.fhir.common.hapi.validation.support.*
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
import org.hl7.fhir.r4.model.DomainResource
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.provider.Arguments

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.nio.charset.StandardCharsets
import java.util.stream.Stream

import static org.junit.jupiter.api.Assertions.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractExportScriptTest<E extends DomainResource> {

    public static final String METHOD_SOURCE = "getTestData"
    private List<Arguments> mappingResults

    @BeforeAll
    void setUp() {

        final TestResources resources = this.class.getAnnotation(TestResources)

        final Validate validate = this.class.getAnnotation(Validate)

        final FhirValidator validator = setUpValidator(validate)


        if (resources == null) {
            throw new IllegalStateException("TestResources Annotation is missing ion the test class")
        }

        final def groovyPath = resources.groovyScriptPath()
        final def contextMapsPath = resources.contextMapsPath()

        if (groovyPath == null || contextMapsPath == null) {
            throw new IllegalArgumentException("The TestResourcesAnnotation parameters must be given.")
        }

        loadAndTransform(groovyPath, contextMapsPath, validator)
    }

    private void loadAndTransform(@Nonnull final String groovyPath,
                                  @Nonnull final String contextMapsPath,
                                  final FhirValidator validator) throws FileNotFoundException {
        final List<Map<String, Object>> contexts = createTestData(contextMapsPath)
        final Fhir4ScriptRunner runner = createRunner(groovyPath)
        mappingResults = contexts.collect {
            final Context context = new Context(it)
            final E resource = (E) runner.run(context)
            if (validator != null && resource.hasId()) {
                final ValidationResult result = validator.validateWithResult(resource)
                if (!result.isSuccessful()) {
                    fail("Resource Validation failed:\n" +
                            String.join("\n",
                                    result.getMessages()
                                            .findAll { it.getSeverity() == ResultSeverityEnum.ERROR }
                                            .collect { it.toString() }))
                }
            }
            return Arguments.of(context, resource)
        }.asImmutable()
    }

    @Nonnull
    static List<Map<String, Object>> createTestData(@Nonnull final String contextMapsPath) throws FileNotFoundException {

        final projectPath = ConfigReader.getProperty(ConfigReader.TEST_CONFIG,"test.data.project")
        FileInputStream is;
        if (projectPath) {
            is = new FileInputStream(contextMapsPath.replaceAll(/src\/test\/resources\/projects\/([^\/]+)\//,
                    "src/test/resources/projects/${projectPath}/"))
        } else
            is = new FileInputStream(contextMapsPath)

        return new JsonSlurper().parse(is) as List<Map<String, Object>>
    }

    @Nonnull
    static Fhir4ScriptRunner createRunner(@Nonnull final String groovyPath) {
        final FileInputStream is = new FileInputStream(groovyPath)
        return getFhir4ScriptRunner(is, "test")
    }

    @Nonnull
    private static Fhir4ScriptRunner getFhir4ScriptRunner(final InputStream is, final String className) throws UnsupportedEncodingException {
        final InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)
        final Fhir4ScriptEngine engine = new Fhir4ScriptEngine()
        return engine.create(reader, className)
    }

    protected Stream<Arguments> getTestData() {
        return mappingResults.stream()
    }

    @Nullable
    private static FhirValidator setUpValidator(final Validate validate) {

        if (validate == null) {
            return null
        }

        final FhirContext context = FhirContext.forR4()


        final NpmPackageValidationSupport npmPackageValidationSupport = new NpmPackageValidationSupport(context)


        final File packageDirFile = new File(validate.packageDir())
        packageDirFile.eachFile { final file ->
            npmPackageValidationSupport.loadPackageFromClasspath("${packageDirFile.name}/${file.name}")
        }

        final ValidationSupportChain supportChain = new ValidationSupportChain(
                npmPackageValidationSupport,
                new DefaultProfileValidationSupport(context),
                new InMemoryTerminologyServerValidationSupport(context),
                new SnapshotGeneratingValidationSupport(context))

        final CachingValidationSupport validationSupport = new CachingValidationSupport(supportChain);

        final FhirValidator validator = context.newValidator()

        final FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
        validator.registerValidatorModule(instanceValidator)
        instanceValidator.setNoTerminologyChecks(true)

        return validator
    }
}
