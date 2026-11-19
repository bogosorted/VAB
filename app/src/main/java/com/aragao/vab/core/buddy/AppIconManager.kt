package com.aragao.vab.core.buddy

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import com.aragao.vab.MainActivity

object AppIconManager {

    private const val ALIAS_COUNT = 10

    /**
     * Sincroniza o ícone do app no Launcher do Android ativando o `<activity-alias>`
     * correspondente ao estilo do seu Buddy atual (`dna.headTrait.ordinal`).
     */
    fun syncLauncherAlias(context: Context, dna: BuddyDna) {
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            val targetIdx = dna.headTrait.ordinal.coerceIn(0, ALIAS_COUNT - 1)
            val targetComp = ComponentName(pkg, "$pkg.BuddyIconAlias$targetIdx")

            val currentTargetState = pm.getComponentEnabledSetting(targetComp)
            val isTargetEnabled = currentTargetState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (targetIdx == 0 && currentTargetState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

            if (!isTargetEnabled) {
                pm.setComponentEnabledSetting(
                    targetComp,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            for (i in 0 until ALIAS_COUNT) {
                if (i == targetIdx) continue
                val comp = ComponentName(pkg, "$pkg.BuddyIconAlias$i")
                val state = pm.getComponentEnabledSetting(comp)
                val isCurrentlyEnabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (i == 0 && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                if (isCurrentlyEnabled) {
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }

    /**
     * Renderiza o sprite 16x16 exato do Buddy (com suas cores pastéis, roupinha, olhos e traços)
     * em um Bitmap nítido para a barra de multitarefas (Recents) e atalhos na tela inicial.
     */
    fun createExactBuddyIconBitmap(dna: BuddyDna, sizePx: Int = 192): Bitmap {
        val grid = BuddyGenerator.GRID_SIZE
        val pixels = BuddyGenerator.renderFrame(dna, BuddyVisualState.IDLE_CHILL, tick = 0)
        val rawSprite = Bitmap.createBitmap(grid, grid, Bitmap.Config.ARGB_8888)
        rawSprite.setPixels(pixels, 0, grid, 0, 0, grid, grid)

        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF7F5F0")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dna.outlineColor
            style = Paint.Style.STROKE
            strokeWidth = (sizePx * 0.035f).coerceAtLeast(2f)
        }
        val corner = sizePx * 0.24f
        val rect = RectF(4f, 4f, sizePx - 4f, sizePx - 4f)
        canvas.drawRoundRect(rect, corner, corner, bgPaint)
        canvas.drawRoundRect(rect, corner, corner, borderPaint)

        val pixelPaint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            isDither = false
        }
        val pad = (sizePx * 0.13f).toInt()
        val dst = Rect(pad, pad, sizePx - pad, sizePx - pad)
        canvas.drawBitmap(rawSprite, null, dst, pixelPaint)
        return out
    }

    /**
     * Atualiza o ícone exibido na tela de apps recentes do Android com o Bitmap 16x16 exato do Buddy.
     */
    @Suppress("DEPRECATION")
    fun updateActivityTaskDescription(activity: Activity, dna: BuddyDna) {
        runCatching {
            val bmp = createExactBuddyIconBitmap(dna, 128)
            activity.setTaskDescription(
                ActivityManager.TaskDescription("VAB (${dna.code})", bmp, Color.parseColor("#FFF7F5F0"))
            )
        }
    }

    /**
     * Atualiza atalhos dinâmicos e permite fixar na tela inicial um ícone com o Bitmap 100% exato
     * das cores/roupinha procedurais do Buddy atual.
     */
    fun pinExactBuddyShortcut(context: Context, dna: BuddyDna) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return
            val bmp = createExactBuddyIconBitmap(dna, 192)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val info = ShortcutInfo.Builder(context, "vab_buddy_${dna.code}")
                .setShortLabel("VAB")
                .setLongLabel("VAB (${dna.code})")
                .setIcon(Icon.createWithBitmap(bmp))
                .setIntent(launchIntent)
                .build()
            sm.dynamicShortcuts = listOf(info)
            if (sm.isRequestPinShortcutSupported) {
                sm.requestPinShortcut(info, null)
            }
        }
    }
}
