package buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JUnitTestSignatureSupportTest {
    @Test
    fun `accepts void tests and rejects value returning tests`() {
        val invalid =
            JUnitTestSignatureSupport.invalidMethods(
                sequenceOf(ValidTest::class.java, InvalidTest::class.java),
            )

        assertEquals(1, invalid.size)
        assertTrue(invalid.single().contains("InvalidTest#returnsValue"))
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
