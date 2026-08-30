package com.sktpj.dynavorgmonsters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log

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
    private const val TAG = "DVMCharacter"

    val all: List<CharacterDefinition> = listOf(
        CharacterDefinition(
            id = "minotaur",
            displayName = "ミノタウロス",
            assetDirectory = "sprites/minotaur",
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
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
            if (bitmap == null) {
                Log.e(TAG, "sprite_load_failed id=${definition.id} source=drawable")
            } else {
                Log.i(TAG, "sprite_loaded id=${definition.id} width=${bitmap.width} height=${bitmap.height}")
            }
            return bitmap
        }

        val directory = definition.assetDirectory ?: return null
        val partNames = context.assets.list(directory)?.sorted().orEmpty()
        if (partNames.isEmpty()) {
            Log.e(TAG, "sprite_load_failed id=${definition.id} source=assets reason=no_parts")
            return null
        }

        val encoded = buildString {
            partNames.forEach { fileName ->
                context.assets.open("$directory/$fileName").bufferedReader().use { reader ->
                    append(reader.readText().trim())
                }
            }
        }
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .onFailure { Log.e(TAG, "sprite_decode_failed id=${definition.id}", it) }
            .getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            Log.e(TAG, "sprite_load_failed id=${definition.id} source=assets reason=bitmap_decode")
        } else {
            Log.i(TAG, "sprite_loaded id=${definition.id} width=${bitmap.width} height=${bitmap.height}")
        }
        return bitmap
    }
}
