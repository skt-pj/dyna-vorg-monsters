package com.sktpj.dynavorgmonsters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class CharacterSelectView(
    context: Context,
    private val onStartBattle: (CharacterDefinition, CharacterDefinition) -> Unit,
) : FrameLayout(context) {
    private companion object {
        const val TAG = "DVMSelection"
    }

    private val roster = CharacterRoster.all
    private val state = CharacterSelectionState(roster.size)
    private val bitmaps: List<Bitmap?> = roster.map { CharacterRoster.loadBitmap(context, it) }
    private val playerCards = mutableListOf<View>()
    private val enemyCards = mutableListOf<View>()
    private lateinit var startButton: Button

    init {
        keepScreenOn = true
        setBackgroundColor(Color.rgb(18, 21, 32))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
        }
        addView(
            root,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        root.addView(
            TextView(context).apply {
                text = "キャラクター選択"
                setTextColor(Color.WHITE)
                textSize = 24f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42),
            ),
        )

        addSelectionSection(
            root = root,
            title = "自分",
            isPlayer = true,
        )
        addSelectionSection(
            root = root,
            title = "敵",
            isPlayer = false,
        )

        startButton = Button(context).apply {
            text = "バトル開始"
            textSize = 18f
            isAllCaps = false
            isEnabled = false
            alpha = 0.55f
            setOnClickListener {
                val pair = state.selectedPair()
                if (pair == null) {
                    Log.w(TAG, "battle_start_rejected player=${state.playerIndex} enemy=${state.enemyIndex}")
                    return@setOnClickListener
                }
                val player = roster[pair.first]
                val enemy = roster[pair.second]
                Log.i(TAG, "battle_start_accepted player=${player.id} enemy=${enemy.id}")
                onStartBattle(player, enemy)
            }
        }
        root.addView(
            startButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56),
            ).apply {
                topMargin = dp(6)
            },
        )

        Log.i(TAG, "selection_view_ready roster=${roster.joinToString(",") { it.id }}")
    }

    private fun addSelectionSection(
        root: LinearLayout,
        title: String,
        isPlayer: Boolean,
    ) {
        root.addView(
            TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 17f
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(26),
            ),
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        root.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply {
                bottomMargin = dp(4)
            },
        )

        roster.indices.forEach { index ->
            val card = createCharacterCard(index, isPlayer)
            row.addView(
                card,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f,
                ).apply {
                    marginStart = dp(5)
                    marginEnd = dp(5)
                },
            )
            if (isPlayer) {
                playerCards += card
            } else {
                enemyCards += card
            }
        }
    }

    private fun createCharacterCard(index: Int, isPlayer: Boolean): View {
        val definition = roster[index]
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            isClickable = true
            isFocusable = true
            background = cardBackground(selected = false)

            val preview = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                adjustViewBounds = false
                contentDescription = definition.displayName
                val bitmap = bitmaps[index]
                if (bitmap != null) {
                    setImageBitmap(firstFrame(bitmap, definition))
                } else {
                    Log.e(TAG, "preview_missing id=${definition.id}")
                }
            }
            addView(
                preview,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )

            addView(
                TextView(context).apply {
                    text = definition.displayName
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    maxLines = 1
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(28),
                ),
            )

            setOnClickListener {
                if (isPlayer) {
                    state.selectPlayer(index)
                    Log.i(TAG, "character_selected side=player id=${definition.id}")
                } else {
                    state.selectEnemy(index)
                    Log.i(TAG, "character_selected side=enemy id=${definition.id}")
                }
                updateSelectionUi()
            }
        }
    }

    private fun firstFrame(bitmap: Bitmap, definition: CharacterDefinition): Bitmap {
        val frameWidth = bitmap.width / definition.columns
        val frameHeight = bitmap.height / definition.rows
        return Bitmap.createBitmap(bitmap, 0, 0, frameWidth, frameHeight)
    }

    private fun updateSelectionUi() {
        playerCards.forEachIndexed { index, view ->
            view.background = cardBackground(state.playerIndex == index)
        }
        enemyCards.forEachIndexed { index, view ->
            view.background = cardBackground(state.enemyIndex == index)
        }
        startButton.isEnabled = state.ready
        startButton.alpha = if (state.ready) 1f else 0.55f
        Log.i(TAG, "selection_state player=${state.playerIndex} enemy=${state.enemyIndex} ready=${state.ready}")
    }

    private fun cardBackground(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(
                if (selected) {
                    Color.rgb(78, 60, 48)
                } else {
                    Color.rgb(35, 39, 50)
                },
            )
            setStroke(
                dp(if (selected) 3 else 1),
                if (selected) {
                    Color.rgb(255, 211, 116)
                } else {
                    Color.rgb(100, 108, 126)
                },
            )
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
