package buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JUnitTestSignatureSupportTest {
    @Test
    fun `accepts void tests and rejects value returning tests and templates`() {
        val invalid =
            JUnitTestSignatureSupport.invalidMethods(
                sequenceOf(ValidTest::class.java, InvalidTest::class.java, InvalidParameterizedTest::class.java),
            )

        assertEquals(2, invalid.size)
        assertTrue(invalid.any { it.contains("InvalidTest#returnsValue") })
        assertTrue(invalid.any { it.contains("InvalidParameterizedTest#returnsValue") })
    }

    @Test
    fun `finds parameterized repeated composed and factory entrypoints`() {
        val entries =
            JUnitTestSignatureSupport.entryPoints(
                sequenceOf(AllEntryPointKinds::class.java),
            )

        assertEquals(4, entries.size)
        assertTrue(entries.any { it.contains("#parameterized") })
        assertTrue(entries.any { it.contains("#repeated") })
        assertTrue(entries.any { it.contains("#composed") })
        assertTrue(entries.any { it.contains("#factory") })
    }
}

private class ValidTest {
    @Test
    fun returnsUnit() = Unit
}

private class InvalidTest {
    @Test
    fun returnsValue(): String = "not a valid JUnit test signature"
}

private class InvalidParameterizedTest {
    @ParameterizedTest
    @ValueSource(strings = ["value"])
    fun returnsValue(value: String): String = value
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@RepeatedTest(1)
private annotation class ComposedRepeatedTest

private class AllEntryPointKinds {
    @ParameterizedTest
    @ValueSource(strings = ["value"])
    fun parameterized(value: String) = Unit

    @RepeatedTest(1)
    fun repeated() = Unit

    @ComposedRepeatedTest
    fun composed() = Unit

    @TestFactory
    fun factory(): List<Any> = emptyList()
}
