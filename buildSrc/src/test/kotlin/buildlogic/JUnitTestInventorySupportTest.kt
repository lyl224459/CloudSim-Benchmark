package buildlogic

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JUnitTestInventorySupportTest {
    @Test
    fun `renders and parses sorted stable inventory`() {
        val rendered = JUnitTestInventorySupport.render(listOf("z.Test#b()", "a.Test#a()"))

        assertEquals(
            listOf("format=1", "count=2", "entry=a.Test#a()", "entry=z.Test#b()"),
            rendered.lineSequence().filter(String::isNotBlank).toList(),
        )
        assertEquals(listOf("a.Test#a()", "z.Test#b()"), JUnitTestInventorySupport.parse(rendered))
    }

    @Test
    fun `reports added and removed entrypoints`() {
        val difference =
            JUnitTestInventorySupport.difference(
                expected = listOf("old.Test#scenario()"),
                actual = listOf("new.Test#scenario()"),
            )

        assertContains(difference.orEmpty(), "- old.Test#scenario()")
        assertContains(difference.orEmpty(), "+ new.Test#scenario()")
        assertNull(JUnitTestInventorySupport.difference(listOf("same"), listOf("same")))
    }

    @Test
    fun `rejects malformed inventory`() {
        assertFailsWith<GradleException> {
            JUnitTestInventorySupport.parse("format=1\ncount=2\nentry=only.One#test()\n")
        }
        assertFailsWith<GradleException> {
            JUnitTestInventorySupport.parse("format=1\ncount=2\nentry=z\nentry=a\n")
        }
        assertFailsWith<GradleException> {
            JUnitTestInventorySupport.parse("format=99\ncount=0\n")
        }
    }
}
