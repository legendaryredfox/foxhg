package io.github.romulofer.foxhg.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppModelTest {
    private fun withFile(bytes: ByteArray, block: (Path) -> Unit) {
        val file = Files.createTempFile("foxhg", ".nds")
        try {
            Files.write(file, bytes)
            block(file)
        } finally {
            Files.delete(file)
        }
    }

    private fun headerWith(code: String) = ByteArray(0x200).also { b -> code.forEachIndexed { i, c -> b[0x0C + i] = c.code.toByte() } }

    @Test
    fun rejectsFileThatIsNotARom() = withFile(ByteArray(32)) { file ->
        val model = testModel()
        model.openRom(file)
        assertNull(model.header)
        assertNull(model.project)
        assertNotNull(model.error)
    }

    @Test
    fun rejectsUnsupportedGames() = withFile(headerWith("BPEE")) { file ->
        val model = testModel()
        model.openRom(file)
        assertNull(model.project)
        assertTrue(model.error!!.contains("not supported"), model.error)
    }

    @Test
    fun rejectsARomWithoutAFileSystem() = withFile(headerWith("IPKE")) { file ->
        val model = testModel()
        model.openRom(file)
        assertNull(model.project, "a header alone is not a usable ROM")
        assertNotNull(model.error)
        model.close()
    }
}
