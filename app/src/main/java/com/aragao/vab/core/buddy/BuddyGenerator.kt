package com.aragao.vab.core.buddy

import kotlin.math.abs
import kotlin.math.roundToInt

enum class BuddyVisualState {
    IDLE_CHILL,
    IDLE_STRESSED_P1,
    LISTENING,
    THINKING,
    CELEBRATING
}

data class BuddyDna(
    val seed: Long,
    val code: String,
    val primaryColor: Int,
    val highlightColor: Int,
    val shadowColor: Int,
    val outlineColor: Int,
    val eyeColor: Int,
    val accentColor: Int,
    val headTrait: HeadTrait,
    val eyeStyle: EyeStyle
) {
    enum class HeadTrait { EARS, ANTENNA, HORNS, TUFT, ROUND }
    enum class EyeStyle { CLASSIC, BIG_KAWAII, VISOR, WIDE }
}

/**
 * PRNG determinístico de 64 bits (SplitMix64) para garantir que o mesmo ANDROID_ID
 * gere sempre exatamente o mesmo Tamagotchi em qualquer execução.
 */
internal class SplitMix64(seed: Long) {
    private var state: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

    fun nextLong(): Long {
        var z = (state + -0x61c8864680b583ebL).also { state = it }
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun nextFloat(): Float {
        return ((nextLong() ushr 40).toDouble() / (1L shl 24).toDouble()).toFloat()
    }
}

object BuddyGenerator {
    const val GRID_SIZE = 16

    fun seedFromDeviceId(deviceId: String): Long {
        val normalized = deviceId.trim().ifEmpty { "vab-default-device" }
        var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
        for (ch in normalized) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L
        }
        return hash
    }

    fun formatDnaCode(seed: Long): String {
        val upper = ((seed ushr 32) and 0xFFFFL).toString(16).uppercase().padStart(4, '0')
        val lower = (seed and 0xFFFFL).toString(16).uppercase().padStart(4, '0')
        return "VAB-$upper-$lower"
    }

    fun generateDna(deviceId: String): BuddyDna = generateDnaFromSeed(seedFromDeviceId(deviceId))

    fun generateDnaFromSeed(seed: Long): BuddyDna {
        val rng = SplitMix64(seed)
        val hue = rng.nextFloat() * 360f
        val sat = 0.62f + rng.nextFloat() * 0.28f
        val light = 0.54f + rng.nextFloat() * 0.14f

        val primary = hslToArgb(hue, sat, light)
        val highlight = hslToArgb((hue + 10f) % 360f, (sat * 0.9f).coerceAtMost(1f), (light + 0.18f).coerceAtMost(0.88f))
        val shadow = hslToArgb((hue + 350f) % 360f, sat, (light - 0.17f).coerceAtLeast(0.24f))
        val outline = hslToArgb(hue, (sat * 0.65f).coerceAtMost(1f), 0.13f)
        val accentHue = (hue + 145f + rng.nextFloat() * 70f) % 360f
        val accent = hslToArgb(accentHue, 0.85f, 0.62f)
        val eyeColor = 0xFF141820.toInt()

        val headTraits = BuddyDna.HeadTrait.entries
        val eyeStyles = BuddyDna.EyeStyle.entries

        return BuddyDna(
            seed = seed,
            code = formatDnaCode(seed),
            primaryColor = primary,
            highlightColor = highlight,
            shadowColor = shadow,
            outlineColor = outline,
            eyeColor = eyeColor,
            accentColor = accent,
            headTrait = headTraits[rng.nextInt(headTraits.size)],
            eyeStyle = eyeStyles[rng.nextInt(eyeStyles.size)]
        )
    }

    /**
     * Renderiza um frame 16x16 ARGB (256 ints) simétrico com contorno automático de 1px
     * e animações procedurais para os estados do botão flutuante.
     */
    fun renderFrame(
        dna: BuddyDna,
        state: BuddyVisualState,
        tick: Int,
        audioAmplitude: Float = 0f
    ): IntArray {
        val rng = SplitMix64(dna.seed xor 0x5A5A5A5AL)
        val bodyMask = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }
        val accentMask = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }

        // Deslocamento vertical de animação (respiração ou pulinho)
        val yShift = when (state) {
            BuddyVisualState.CELEBRATING -> if (tick % 2 == 0) -1 else 0
            BuddyVisualState.LISTENING -> if (audioAmplitude > 0.25f && tick % 2 == 0) -1 else 0
            else -> if (tick % 6 >= 3) 1 else 0
        }

        fun setSymBody(hx: Int, y: Int, value: Boolean = true) {
            val sy = y + yShift
            if (hx in 0..7 && sy in 1..(GRID_SIZE - 2)) {
                bodyMask[sy][hx] = value
                bodyMask[sy][GRID_SIZE - 1 - hx] = value
            }
        }

        fun setSymAccent(hx: Int, y: Int) {
            val sy = y + yShift
            if (hx in 0..7 && sy in 1..(GRID_SIZE - 2)) {
                accentMask[sy][hx] = true
                accentMask[sy][GRID_SIZE - 1 - hx] = true
            }
        }

        // Núcleo garantido do Tamagotchi (sempre conectado e fofo)
        for (y in 5..12) {
            for (hx in 4..7) {
                setSymBody(hx, y, true)
            }
        }

        // Bochechas / largura lateral procedural por linha
        for (y in 5..12) {
            val edgeChance = if (y in 7..11) 0.78f else 0.42f
            if (rng.nextFloat() < edgeChance) {
                setSymBody(3, y, true)
                if (y in 8..10 && rng.nextFloat() < 0.45f) {
                    setSymBody(2, y, true)
                }
            }
        }

        // Topo da cabeça arredondado
        for (hx in 5..7) setSymBody(hx, 4, true)
        if (rng.nextFloat() < 0.65f) setSymBody(4, 4, true)

        // Traço procedural do topo da cabeça
        when (dna.headTrait) {
            BuddyDna.HeadTrait.EARS -> {
                setSymBody(3, 3, true)
                setSymBody(4, 3, true)
                setSymBody(3, 2, true)
                setSymAccent(3, 3)
            }
            BuddyDna.HeadTrait.ANTENNA -> {
                val sy2 = (2 + yShift).coerceIn(1, 14)
                val sy3 = (3 + yShift).coerceIn(1, 14)
                bodyMask[sy3][7] = true
                bodyMask[sy3][8] = true
                accentMask[sy2][7] = true
                accentMask[sy2][8] = true
                bodyMask[sy2][7] = true
                bodyMask[sy2][8] = true
            }
            BuddyDna.HeadTrait.HORNS -> {
                setSymBody(4, 3, true)
                setSymBody(3, 2, true)
                setSymAccent(3, 2)
            }
            BuddyDna.HeadTrait.TUFT -> {
                setSymBody(6, 3, true)
                setSymBody(7, 3, true)
                setSymAccent(7, 2)
                setSymBody(7, 2, true)
            }
            BuddyDna.HeadTrait.ROUND -> {
                setSymBody(5, 3, true)
                setSymBody(6, 3, true)
                setSymBody(7, 3, true)
            }
        }

        // Pezinhos procedurais na base
        val footX = if (rng.nextFloat() < 0.5f) 4 else 5
        setSymBody(footX, 13, true)
        setSymBody(footX + 1, 13, true)

        // Garante conectividade via flood-fill a partir do centro
        val connected = keepConnectedToCenter(bodyMask, 7, (8 + yShift).coerceIn(1, 14))

        val pixels = IntArray(GRID_SIZE * GRID_SIZE) { 0x00000000 }

        // 1) Preenche corpo com sombreamento top-down + detalhes de destaque
        var minBodyY = GRID_SIZE
        var maxBodyY = 0
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (connected[y][x]) {
                    if (y < minBodyY) minBodyY = y
                    if (y > maxBodyY) maxBodyY = y
                }
            }
        }

        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (connected[y][x]) {
                    val color = when {
                        accentMask[y][x] -> dna.accentColor
                        y <= minBodyY + 1 -> dna.highlightColor
                        y >= maxBodyY - 1 -> dna.shadowColor
                        else -> dna.primaryColor
                    }
                    pixels[y * GRID_SIZE + x] = color
                }
            }
        }

        // 2) Contorno automático escuro de 1px ao redor da silhueta (contraste Tamagotchi)
        val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (!connected[y][x]) {
                    val touchesBody = dirs.any { d ->
                        val ny = y + d[1]
                        val nx = x + d[0]
                        ny in 0 until GRID_SIZE && nx in 0 until GRID_SIZE && connected[ny][nx]
                    }
                    if (touchesBody) {
                        pixels[y * GRID_SIZE + x] = dna.outlineColor
                    }
                }
            }
        }

        // 3) Olhinhos procedurais e expressões
        val eyeY = (7 + yShift).coerceIn(3, 11)
        val isBlinking = (state == BuddyVisualState.IDLE_CHILL || state == BuddyVisualState.IDLE_STRESSED_P1) && (tick % 14 == 13)

        fun setPx(x: Int, y: Int, color: Int) {
            if (x in 0 until GRID_SIZE && y in 0 until GRID_SIZE) {
                pixels[y * GRID_SIZE + x] = color
            }
        }

        when {
            state == BuddyVisualState.CELEBRATING -> {
                // Olhinhos felizes ^ ^
                setPx(5, eyeY, dna.eyeColor)
                setPx(6, eyeY - 1, dna.eyeColor)
                setPx(9, eyeY - 1, dna.eyeColor)
                setPx(10, eyeY, dna.eyeColor)
            }
            isBlinking -> {
                setPx(5, eyeY, dna.eyeColor)
                setPx(6, eyeY, dna.eyeColor)
                setPx(9, eyeY, dna.eyeColor)
                setPx(10, eyeY, dna.eyeColor)
            }
            state == BuddyVisualState.THINKING -> {
                // Olhando para cima + balãozinho "..."
                setPx(5, eyeY - 1, dna.eyeColor)
                setPx(10, eyeY - 1, dna.eyeColor)
                val dotCount = (tick % 3) + 1
                for (i in 0 until dotCount) {
                    setPx(10 + i * 2, 1, 0xFFFFFFFF.toInt())
                    setPx(10 + i * 2, 2, dna.outlineColor)
                }
            }
            else -> {
                when (dna.eyeStyle) {
                    BuddyDna.EyeStyle.BIG_KAWAII -> {
                        for (dy in 0..1) {
                            setPx(5, eyeY + dy, dna.eyeColor)
                            setPx(10, eyeY + dy, dna.eyeColor)
                        }
                        setPx(5, eyeY, 0xFFFFFFFF.toInt())
                        setPx(10, eyeY, 0xFFFFFFFF.toInt())
                    }
                    BuddyDna.EyeStyle.VISOR -> {
                        for (vx in 5..10) {
                            setPx(vx, eyeY, dna.eyeColor)
                        }
                        setPx(6, eyeY, dna.accentColor)
                        setPx(9, eyeY, dna.accentColor)
                    }
                    BuddyDna.EyeStyle.WIDE -> {
                        setPx(4, eyeY, dna.eyeColor)
                        setPx(11, eyeY, dna.eyeColor)
                    }
                    BuddyDna.EyeStyle.CLASSIC -> {
                        setPx(5, eyeY, dna.eyeColor)
                        setPx(10, eyeY, dna.eyeColor)
                    }
                }
            }
        }

        // 4) Boca e indicadores extras de estado (voz / estresse P1)
        val mouthY = (eyeY + 2).coerceIn(4, 13)
        when (state) {
            BuddyVisualState.LISTENING -> {
                val openHeight = when {
                    audioAmplitude > 0.35f -> 2
                    audioAmplitude > 0.08f -> 1
                    else -> if (tick % 2 == 0) 1 else 0
                }
                setPx(7, mouthY, dna.eyeColor)
                setPx(8, mouthY, dna.eyeColor)
                if (openHeight >= 1) {
                    setPx(7, mouthY + 1, 0xFFFF5252.toInt())
                    setPx(8, mouthY + 1, 0xFFFF5252.toInt())
                }
                if (openHeight >= 2 && mouthY + 2 < GRID_SIZE - 1) {
                    setPx(7, mouthY + 2, dna.eyeColor)
                    setPx(8, mouthY + 2, dna.eyeColor)
                }
                // Sinalzinho de gravação ativa no canto superior direito
                setPx(13, 2, 0xFFFF3B30.toInt())
                setPx(14, 2, 0xFFFF3B30.toInt())
                setPx(13, 3, 0xFFFF3B30.toInt())
                setPx(14, 3, 0xFFFF3B30.toInt())
            }
            BuddyVisualState.IDLE_STRESSED_P1 -> {
                // Boquinha tensa + gotinha de suor ciano animada na lateral
                setPx(7, mouthY, dna.eyeColor)
                setPx(8, mouthY + (tick % 2), dna.eyeColor)
                val sweatY = 4 + (tick % 3)
                setPx(13, sweatY, 0xFF40C4FF.toInt())
                setPx(13, sweatY + 1, 0xFF00B0FF.toInt())
            }
            BuddyVisualState.CELEBRATING -> {
                setPx(6, mouthY, dna.eyeColor)
                setPx(7, mouthY + 1, dna.eyeColor)
                setPx(8, mouthY + 1, dna.eyeColor)
                setPx(9, mouthY, dna.eyeColor)
            }
            else -> {
                // Sorrisinho sutil
                setPx(7, mouthY, dna.shadowColor)
                setPx(8, mouthY, dna.shadowColor)
            }
        }

        return pixels
    }

    private fun keepConnectedToCenter(
        grid: Array<BooleanArray>,
        startX: Int,
        startY: Int
    ): Array<BooleanArray> {
        val visited = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }
        if (!grid[startY][startX]) return grid

        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startX to startY)
        visited[startY][startX] = true

        val dirs = arrayOf(
            intArrayOf(-1, 0), intArrayOf(1, 0),
            intArrayOf(0, -1), intArrayOf(0, 1)
        )
        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            for (d in dirs) {
                val nx = cx + d[0]
                val ny = cy + d[1]
                if (nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE &&
                    grid[ny][nx] && !visited[ny][nx]
                ) {
                    visited[ny][nx] = true
                    queue.add(nx to ny)
                }
            }
        }
        return visited
    }

    private fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
