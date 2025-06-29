package common

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@MethodSource(AbstractExportScriptTest.METHOD_SOURCE)
@ParameterizedTest
@interface ExportScriptTest {}