package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CharacterSpriteAssetTest {
    @Test
    fun bundledAdditionalSpriteAssetsDecodeToExpectedWebpSizes() {
        val workingDirectory = File(System.getProperty("user.dir"))
        val projectRoot = sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .first { File(it, "app/src/main/assets/sprites").isDirectory }
        assertSprite(projectRoot, "kaiju", expectedWidth = 280, expectedHeight = 320)
        assertSprite(projectRoot, "dragon", expectedWidth = 224, expectedHeight = 256)
    }

    private fun assertSprite(projectRoot: File, id: String, expectedWidth: Int, expectedHeight: Int) {
        val directory = File(projectRoot, "app/src/main/assets/sprites/$id")
        val encoded = directory.listFiles()!!.sortedBy { it.name }.joinToString("") { it.readText().trim() }
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        assertTrue(bytes.size > 30)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WEBP", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals("VP8X", bytes.copyOfRange(12, 16).toString(Charsets.US_ASCII))
        assertEquals(expectedWidth, readVp8xDimension(bytes, 24))
        assertEquals(expectedHeight, readVp8xDimension(bytes, 27))
    }

    private fun readVp8xDimension(bytes: ByteArray, offset: Int): Int =
        1 + (bytes[offset].toInt() and 0xff) +
            ((bytes[offset + 1].toInt() and 0xff) shl 8) +
            ((bytes[offset + 2].toInt() and 0xff) shl 16)
}
