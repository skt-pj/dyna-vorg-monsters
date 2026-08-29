package com.sktpj.dynavorgmonsters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

data class CharacterDefinition(
    val id: String,
    val displayName: String,
    val drawableResource: Int? = null,
    val assetDirectory: String? = null,
    val columns: Int,
    val rows: Int = 8,
    val fps: Float = 15f,
    val drawWidth: Float,
    val drawHeight: Float,
)

object CharacterRoster {
    val all: List<CharacterDefinition> = listOf(
        CharacterDefinition(
            id = "minotaur",
            displayName = "ミノタウロス",
            drawableResource = R.drawable.spikeman_minotaur_sprite_sheet,
            columns = 8,
            drawWidth = 430f,
            drawHeight = 430f,
        ),
        CharacterDefinition(
            id = "kaiju",
            displayName = "追加キャラ1",
            assetDirectory = "sprites/kaiju",
            columns = 7,
            drawWidth = 520f,
            drawHeight = 430f,
        ),
        CharacterDefinition(
            id = "dragon",
            displayName = "追加キャラ2",
            assetDirectory = "sprites/dragon",
            columns = 7,
            drawWidth = 540f,
            drawHeight = 400f,
        ),
    )

    fun loadBitmap(context: Context, definition: CharacterDefinition): Bitmap? {
        definition.drawableResource?.let { resourceId ->
            return BitmapFactory.decodeResource(context.resources, resourceId)
        }

        val directory = definition.assetDirectory ?: return null
        val partNames = context.assets.list(directory)?.sorted().orEmpty()
        if (partNames.isEmpty()) return null

        val encoded = buildString {
            partNames.forEach { fileName ->
                context.assets.open("$directory/$fileName").bufferedReader().use { reader ->
                    append(reader.readText().trim())
                }
            }
        }
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
