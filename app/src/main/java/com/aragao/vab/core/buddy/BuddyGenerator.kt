package com.aragao.vab.core.buddy

import kotlin.math.abs
import kotlin.math.roundToInt

enum class HeadTrait {
    CAT_EARS,
    BEAR_EARS,
    BUNNY_EARS,
    SPROUT,
    CROWN,
    BEANIE,
    HALO,
    HORNS,
    ANTENNA,
    BOW
}

enum class EyeStyle {
    DOTS,
    SPARKLE,
    HAPPY_ARC,
    WIDE_CUTE,
    SLEEPY_CUTE,
    GLASSES,
    WINK,
    STARRY,
    SHY,
    COOL_SHADES
}

enum class ArmStyle {
    TUCKED,
    OPEN,
    WAVE_LEFT,
    WAVE_RIGHT,
    HUG,
    HIPS,
    UP_CHEER,
    SHORT_PAWS,
    LONG_COZY,
    POCKET
}

enum class LegStyle {
    SHORT_FEET,
    WIDE_STANCE,
    BOOTS,
    SITTING,
    TIPTOE,
    LITTLE_PAWS,
    SNEAKERS,
    WADDLE,
    CROSS_STEP,
    BOUNCY
}

enum class OutfitStyle {
    NONE,
    OVERALLS,
    SCARF,
    STRIPED_TEE,
    HOODIE,
    BOWTIE,
    APRON,
    CAPE_TRIM,
    HEART_SWEATER,
    VEST
}

enum class BuddyVisualState {
    IDLE_CHILL,
    IDLE_CURIOUS,
    IDLE_PLAYFUL,
    IDLE_SLEEPY,
    IDLE_STRESSED_P1,
    LISTENING,
    NOTING,
    ORGANIZING,
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
    val accentColor: Int,
    val outfitColor: Int,
    val outfitAccentColor: Int,
    val eyeColor: Int,
    val blushColor: Int,
    val headTrait: HeadTrait,
    val eyeStyle: EyeStyle,
    val armStyle: ArmStyle,
    val legStyle: LegStyle,
    val outfitStyle: OutfitStyle
)

object BuddyGenerator {
    const val GRID_SIZE = 16
    private const val TRANSPARENT = 0x00000000

    fun generateDna(deviceSeedString: String): BuddyDna {
        val seed = fnv1a64(deviceSeedString)
        return generateDnaFromSeed(seed)
    }

    fun generateDnaFromSeed(seed: Long): BuddyDna {
        val hue = positiveMod(seed, 360).toFloat()
        val outfitHue = (hue + 115f + positiveMod(seed ushr 5, 110).toFloat()) % 360f
        val accentHue = (hue + 45f + positiveMod(seed ushr 10, 70).toFloat()) % 360f

        // Paleta pastel suave e aconchegante (saturação moderada, luminosidade alta)
        val primary = hslToArgb(hue, 0.50f, 0.80f)
        val highlight = hslToArgb(hue, 0.54f, 0.89f)
        val shadow = hslToArgb(hue, 0.44f, 0.69f)
        val outline = hslToArgb(hue, 0.34f, 0.30f)
        val accent = hslToArgb(accentHue, 0.56f, 0.78f)
        val outfit = hslToArgb(outfitHue, 0.48f, 0.72f)
        val outfitAccent = hslToArgb(outfitHue, 0.52f, 0.84f)
        val blush = hslToArgb(350f, 0.68f, 0.80f)

        val headTraits = HeadTrait.entries
        val eyeStyles = EyeStyle.entries
        val armStyles = ArmStyle.entries
        val legStyles = LegStyle.entries
        val outfitStyles = OutfitStyle.entries

        val headTrait = headTraits[positiveMod(seed ushr 14, headTraits.size)]
        val eyeStyle = eyeStyles[positiveMod(seed ushr 20, eyeStyles.size)]
        val armStyle = armStyles[positiveMod(seed ushr 26, armStyles.size)]
        val legStyle = legStyles[positiveMod(seed ushr 32, legStyles.size)]
        val outfitStyle = outfitStyles[positiveMod(seed ushr 38, outfitStyles.size)]

        val shortCode = "VAB-" + abs(seed.toInt()).toString(16).uppercase().padStart(4, '0').take(4)

        return BuddyDna(
            seed = seed,
            code = shortCode,
            primaryColor = primary,
            highlightColor = highlight,
            shadowColor = shadow,
            outlineColor = outline,
            accentColor = accent,
            outfitColor = outfit,
            outfitAccentColor = outfitAccent,
            eyeColor = 0xFF2B2930.toInt(),
            blushColor = blush,
            headTrait = headTrait,
            eyeStyle = eyeStyle,
            armStyle = armStyle,
            legStyle = legStyle,
            outfitStyle = outfitStyle
        )
    }

    /**
     * Renderiza o sprite 16x16 (IntArray de 256 pixels ARGB) combinando as 10 predefinições
     * de cabeça, olhos, roupinha, braços e pernas em tons pastéis suaves.
     */
    fun renderFrame(
        dna: BuddyDna,
        state: BuddyVisualState,
        tick: Int,
        audioAmplitude: Float = 0f
    ): IntArray {
        val pixels = IntArray(GRID_SIZE * GRID_SIZE) { TRANSPARENT }
        val bodyMask = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }

        val bounceY = when (state) {
            BuddyVisualState.CELEBRATING -> if (tick % 2 == 0) -1 else 0
            BuddyVisualState.ORGANIZING -> if (tick % 3 == 0) -1 else 0
            else -> 0
        }

        fun setPx(x: Int, y: Int, color: Int) {
            val yy = y + bounceY
            if (x in 0 until GRID_SIZE && yy in 0 until GRID_SIZE) {
                pixels[yy * GRID_SIZE + x] = color
            }
        }

        fun setSymmetric(x: Int, y: Int, color: Int) {
            setPx(x, y, color)
            setPx(GRID_SIZE - 1 - x, y, color)
        }

        fun markBodySymmetric(x: Int, y: Int) {
            if (x in 0 until GRID_SIZE && y in 0 until GRID_SIZE) {
                bodyMask[y][x] = true
                bodyMask[y][GRID_SIZE - 1 - x] = true
            }
        }

        // 1) Silhueta fofa arredondada 16x16 (estilo Tamagotchi / Slime rechonchudo)
        for (x in 5..7) markBodySymmetric(x, 3)
        for (x in 4..7) markBodySymmetric(x, 4)
        for (y in 5..10) {
            for (x in 3..7) markBodySymmetric(x, y)
        }
        for (x in 4..7) markBodySymmetric(x, 11)
        for (x in 5..7) markBodySymmetric(x, 12)

        // 2) Predefinições de Cabeça (10 estilos)
        when (dna.headTrait) {
            HeadTrait.CAT_EARS -> {
                markBodySymmetric(4, 1)
                markBodySymmetric(4, 2)
                markBodySymmetric(5, 2)
            }
            HeadTrait.BEAR_EARS -> {
                markBodySymmetric(3, 2)
                markBodySymmetric(4, 2)
                markBodySymmetric(3, 3)
                markBodySymmetric(4, 3)
            }
            HeadTrait.BUNNY_EARS -> {
                markBodySymmetric(5, 0)
                markBodySymmetric(5, 1)
                markBodySymmetric(5, 2)
            }
            HeadTrait.SPROUT -> {
                markBodySymmetric(7, 1)
                markBodySymmetric(7, 2)
            }
            HeadTrait.CROWN -> {
                markBodySymmetric(5, 2)
                markBodySymmetric(7, 2)
            }
            HeadTrait.BEANIE -> {
                markBodySymmetric(6, 2)
                markBodySymmetric(7, 2)
            }
            HeadTrait.HALO -> {
                // Halo flutua acima sem grudar no contorno
            }
            HeadTrait.HORNS -> {
                markBodySymmetric(3, 1)
                markBodySymmetric(4, 2)
            }
            HeadTrait.ANTENNA -> {
                markBodySymmetric(7, 1)
                markBodySymmetric(7, 2)
            }
            HeadTrait.BOW -> {
                markBodySymmetric(5, 2)
                markBodySymmetric(6, 2)
            }
        }

        // 3) Predefinições de Braços (10 estilos)
        when (dna.armStyle) {
            ArmStyle.TUCKED -> {
                // Braços recolhidos junto ao corpo
            }
            ArmStyle.OPEN -> {
                markBodySymmetric(2, 7)
                markBodySymmetric(2, 8)
            }
            ArmStyle.WAVE_LEFT -> {
                markBodySymmetric(2, 6)
                markBodySymmetric(2, 7)
            }
            ArmStyle.WAVE_RIGHT -> {
                markBodySymmetric(2, 6)
                markBodySymmetric(2, 8)
            }
            ArmStyle.HUG -> {
                markBodySymmetric(2, 8)
            }
            ArmStyle.HIPS -> {
                markBodySymmetric(2, 8)
                markBodySymmetric(2, 9)
            }
            ArmStyle.UP_CHEER -> {
                markBodySymmetric(2, 5)
                markBodySymmetric(2, 6)
            }
            ArmStyle.SHORT_PAWS -> {
                markBodySymmetric(2, 7)
            }
            ArmStyle.LONG_COZY -> {
                markBodySymmetric(2, 7)
                markBodySymmetric(2, 8)
                markBodySymmetric(2, 9)
            }
            ArmStyle.POCKET -> {
                markBodySymmetric(2, 9)
            }
        }

        // 4) Predefinições de Pernas (10 estilos)
        when (dna.legStyle) {
            LegStyle.SHORT_FEET -> {
                markBodySymmetric(5, 13)
                markBodySymmetric(6, 13)
            }
            LegStyle.WIDE_STANCE -> {
                markBodySymmetric(4, 13)
                markBodySymmetric(5, 13)
            }
            LegStyle.BOOTS -> {
                markBodySymmetric(5, 13)
                markBodySymmetric(6, 13)
                markBodySymmetric(5, 14)
            }
            LegStyle.SITTING -> {
                markBodySymmetric(3, 12)
                markBodySymmetric(4, 13)
            }
            LegStyle.TIPTOE -> {
                markBodySymmetric(5, 13)
                markBodySymmetric(5, 14)
            }
            LegStyle.LITTLE_PAWS -> {
                markBodySymmetric(6, 13)
            }
            LegStyle.SNEAKERS -> {
                markBodySymmetric(4, 13)
                markBodySymmetric(5, 13)
                markBodySymmetric(6, 13)
            }
            LegStyle.WADDLE -> {
                markBodySymmetric(3, 12)
                markBodySymmetric(4, 12)
            }
            LegStyle.CROSS_STEP -> {
                markBodySymmetric(6, 13)
                markBodySymmetric(7, 13)
            }
            LegStyle.BOUNCY -> {
                markBodySymmetric(4, 13)
                markBodySymmetric(5, 14)
            }
        }

        // 5) Preenche corpo com sombreamento pastel suave
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (bodyMask[y][x]) {
                    val fill = when {
                        y <= 4 -> dna.highlightColor
                        y >= 11 -> dna.shadowColor
                        x <= 3 || x >= 12 -> dna.shadowColor
                        else -> dna.primaryColor
                    }
                    setPx(x, y, fill)
                }
            }
        }

        // 6) Contorno pastel escuro 1px ao redor da silhueta
        val dirs = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(-1, 0),
            intArrayOf(0, 1),
            intArrayOf(0, -1)
        )
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (!bodyMask[y][x]) {
                    val touchesBody = dirs.any { d ->
                        val nx = x + d[0]
                        val ny = y + d[1]
                        nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE && bodyMask[ny][nx]
                    }
                    if (touchesBody) {
                        setPx(x, y, dna.outlineColor)
                    }
                }
            }
        }

        // 7) Detalhes coloridos dos acessórios de cabeça
        when (dna.headTrait) {
            HeadTrait.CAT_EARS -> setSymmetric(4, 2, dna.blushColor)
            HeadTrait.BEAR_EARS -> setSymmetric(4, 3, dna.blushColor)
            HeadTrait.BUNNY_EARS -> {
                setSymmetric(5, 1, dna.blushColor)
                setSymmetric(5, 2, dna.blushColor)
            }
            HeadTrait.SPROUT -> {
                setSymmetric(6, 0, 0xFF88C999.toInt())
                setSymmetric(7, 1, 0xFF68B37B.toInt())
            }
            HeadTrait.CROWN -> {
                setSymmetric(5, 2, 0xFFF7D47E.toInt())
                setSymmetric(6, 3, 0xFFF7D47E.toInt())
                setSymmetric(7, 2, 0xFFF7D47E.toInt())
            }
            HeadTrait.BEANIE -> {
                setSymmetric(6, 2, dna.outfitColor)
                setSymmetric(7, 2, dna.outfitAccentColor)
                for (x in 4..7) setSymmetric(x, 3, dna.outfitColor)
            }
            HeadTrait.HALO -> {
                for (x in 5..7) setSymmetric(x, 0, 0xFFF9E08E.toInt())
            }
            HeadTrait.HORNS -> {
                setSymmetric(3, 1, dna.accentColor)
                setSymmetric(4, 2, dna.accentColor)
            }
            HeadTrait.ANTENNA -> {
                setSymmetric(7, 0, dna.accentColor)
            }
            HeadTrait.BOW -> {
                setSymmetric(5, 2, dna.blushColor)
                setSymmetric(6, 2, dna.outfitAccentColor)
                setSymmetric(7, 2, dna.blushColor)
            }
        }

        // 8) Predefinições de Roupinha (10 estilos) nas linhas y = 9..12
        when (dna.outfitStyle) {
            OutfitStyle.NONE -> {
                // Barriguinha pastel clara
                for (x in 6..7) {
                    setSymmetric(x, 9, dna.highlightColor)
                    setSymmetric(x, 10, dna.highlightColor)
                }
            }
            OutfitStyle.OVERALLS -> {
                setSymmetric(5, 9, dna.outfitColor)
                setSymmetric(5, 10, dna.outfitAccentColor)
                for (x in 4..7) {
                    setSymmetric(x, 10, dna.outfitColor)
                    setSymmetric(x, 11, dna.outfitColor)
                    if (x >= 5) setSymmetric(x, 12, dna.outfitColor)
                }
                setSymmetric(5, 10, dna.outfitAccentColor)
            }
            OutfitStyle.SCARF -> {
                for (x in 4..7) setSymmetric(x, 9, dna.outfitColor)
                setSymmetric(6, 10, dna.outfitAccentColor)
                setSymmetric(6, 11, dna.outfitColor)
            }
            OutfitStyle.STRIPED_TEE -> {
                for (x in 3..7) setSymmetric(x, 9, dna.outfitColor)
                for (x in 3..7) setSymmetric(x, 10, dna.outfitAccentColor)
                for (x in 4..7) setSymmetric(x, 11, dna.outfitColor)
            }
            OutfitStyle.HOODIE -> {
                for (y in 9..11) {
                    for (x in 4..7) setSymmetric(x, y, dna.outfitColor)
                }
                setSymmetric(6, 9, dna.outfitAccentColor)
                setSymmetric(7, 11, dna.outfitAccentColor)
            }
            OutfitStyle.BOWTIE -> {
                setSymmetric(6, 9, dna.outfitColor)
                setSymmetric(7, 9, dna.outfitAccentColor)
            }
            OutfitStyle.APRON -> {
                for (y in 9..11) {
                    for (x in 5..7) setSymmetric(x, y, dna.outfitAccentColor)
                }
                setSymmetric(4, 10, dna.outfitColor)
            }
            OutfitStyle.CAPE_TRIM -> {
                setSymmetric(3, 8, dna.outfitColor)
                setSymmetric(3, 9, dna.outfitColor)
                setSymmetric(3, 10, dna.outfitColor)
                setSymmetric(4, 11, dna.outfitColor)
                setSymmetric(7, 9, dna.outfitAccentColor)
            }
            OutfitStyle.HEART_SWEATER -> {
                for (y in 9..11) {
                    for (x in 4..7) setSymmetric(x, y, dna.outfitColor)
                }
                setSymmetric(6, 9, dna.blushColor)
                setSymmetric(7, 10, dna.blushColor)
            }
            OutfitStyle.VEST -> {
                for (y in 9..11) {
                    setSymmetric(4, y, dna.outfitColor)
                    setSymmetric(5, y, dna.outfitColor)
                    setSymmetric(7, y, dna.highlightColor)
                }
            }
        }

        // Detalhe de sapatinho/bota
        if (dna.legStyle == LegStyle.BOOTS) {
            setSymmetric(5, 13, dna.outfitColor)
            setSymmetric(6, 13, dna.outfitColor)
            setSymmetric(5, 14, dna.outfitColor)
        } else if (dna.legStyle == LegStyle.SNEAKERS) {
            setSymmetric(4, 13, dna.outfitAccentColor)
            setSymmetric(5, 13, dna.outfitColor)
            setSymmetric(6, 13, dna.outfitColor)
        }

        // Bochechas fofas pastéis
        setSymmetric(3, 7, dna.blushColor)
        setSymmetric(4, 8, dna.blushColor)

        // 9) Olhos (10 predefinições) e expressão facial
        val isBlinking = state == BuddyVisualState.IDLE_CHILL && (tick % 14 == 13)
        val eyeColor = dna.eyeColor
        val white = 0xFFFFFFFF.toInt()

        when {
            state == BuddyVisualState.IDLE_SLEEPY || isBlinking -> {
                setSymmetric(4, 7, eyeColor)
                setSymmetric(5, 7, eyeColor)
            }
            state == BuddyVisualState.CELEBRATING -> {
                setSymmetric(4, 7, eyeColor)
                setSymmetric(5, 6, eyeColor)
            }
            state == BuddyVisualState.IDLE_STRESSED_P1 -> {
                setSymmetric(4, 6, eyeColor)
                setSymmetric(5, 7, eyeColor)
                setSymmetric(5, 8, eyeColor)
                setPx(12, 5, 0xFF7CC6FE.toInt())
            }
            state == BuddyVisualState.IDLE_CURIOUS -> {
                val shift = if (tick % 4 < 2) 0 else 1
                setPx(4 + shift, 7, eyeColor)
                setPx(10 + shift, 7, eyeColor)
                setPx(4 + shift, 6, white)
                setPx(10 + shift, 6, white)
            }
            else -> {
                when (dna.eyeStyle) {
                    EyeStyle.DOTS -> {
                        setSymmetric(5, 7, eyeColor)
                    }
                    EyeStyle.SPARKLE -> {
                        setSymmetric(4, 6, white)
                        setSymmetric(5, 6, eyeColor)
                        setSymmetric(4, 7, eyeColor)
                        setSymmetric(5, 7, eyeColor)
                    }
                    EyeStyle.HAPPY_ARC -> {
                        setSymmetric(4, 7, eyeColor)
                        setSymmetric(5, 6, eyeColor)
                    }
                    EyeStyle.WIDE_CUTE -> {
                        setSymmetric(4, 6, eyeColor)
                        setSymmetric(4, 7, eyeColor)
                        setSymmetric(5, 6, white)
                        setSymmetric(5, 7, eyeColor)
                    }
                    EyeStyle.SLEEPY_CUTE -> {
                        setSymmetric(4, 6, dna.shadowColor)
                        setSymmetric(5, 6, dna.shadowColor)
                        setSymmetric(4, 7, eyeColor)
                        setSymmetric(5, 7, eyeColor)
                    }
                    EyeStyle.GLASSES -> {
                        for (x in 3..7) setSymmetric(x, 6, dna.accentColor)
                        setSymmetric(3, 7, dna.accentColor)
                        setSymmetric(6, 7, dna.accentColor)
                        setSymmetric(4, 7, eyeColor)
                        setSymmetric(5, 7, white)
                    }
                    EyeStyle.WINK -> {
                        setSymmetric(4, 7, eyeColor)
                        setSymmetric(5, 6, eyeColor)
                        setSymmetric(5, 7, eyeColor)
                    }
                    EyeStyle.STARRY -> {
                        setSymmetric(4, 6, eyeColor)
                        setSymmetric(5, 6, 0xFFF7D47E.toInt())
                        setSymmetric(4, 7, 0xFFF7D47E.toInt())
                        setSymmetric(5, 7, eyeColor)
                    }
                    EyeStyle.SHY -> {
                        setSymmetric(5, 7, eyeColor)
                        setSymmetric(5, 6, white)
                    }
                    EyeStyle.COOL_SHADES -> {
                        for (x in 3..7) setSymmetric(x, 6, eyeColor)
                        setSymmetric(4, 7, eyeColor)
                        setSymmetric(5, 7, eyeColor)
                        setSymmetric(4, 6, white)
                    }
                }
            }
        }

        // 10) Boca + Estados de Ação
        when (state) {
            BuddyVisualState.LISTENING -> {
                setSymmetric(7, 8, eyeColor)
                if (audioAmplitude > 0.14f || tick % 2 == 0) {
                    setSymmetric(7, 9, 0xFFE88D8D.toInt())
                }
            }
            BuddyVisualState.NOTING -> {
                setSymmetric(7, 8, eyeColor)
                // Caderninho + lápis no canto inferior direito
                for (ny in 9..12) {
                    for (nx in 12..14) {
                        setPx(nx, ny, 0xFFFFFBF2.toInt())
                    }
                }
                val penX = if (tick % 2 == 0) 11 else 12
                setPx(penX, 8, 0xFFE69F52.toInt())
                setPx(penX + 1, 9, eyeColor)
            }
            BuddyVisualState.ORGANIZING -> {
                setSymmetric(7, 8, eyeColor)
                val sparkleY = if (tick % 2 == 0) 2 else 3
                setPx(1, sparkleY, 0xFFF7D47E.toInt())
                setPx(14, 5 - sparkleY, 0xFF8CC4F7.toInt())
            }
            BuddyVisualState.CELEBRATING -> {
                setSymmetric(6, 8, eyeColor)
                setSymmetric(7, 9, 0xFFE88D8D.toInt())
                setPx(1, 2, 0xFFF7D47E.toInt())
                setPx(14, 2, 0xFF88C999.toInt())
            }
            BuddyVisualState.IDLE_PLAYFUL -> {
                setSymmetric(6, 8, eyeColor)
                setSymmetric(7, 8, eyeColor)
                if (tick % 2 == 0) {
                    setPx(13, 3, 0xFFF7D47E.toInt())
                }
            }
            BuddyVisualState.IDLE_SLEEPY -> {
                setSymmetric(7, 8, eyeColor)
                val zY = if (tick % 2 == 0) 2 else 1
                setPx(13, zY, 0xFF9AB8E8.toInt())
                setPx(14, zY, 0xFF9AB8E8.toInt())
            }
            BuddyVisualState.IDLE_STRESSED_P1 -> {
                setSymmetric(6, 9, eyeColor)
                setSymmetric(7, 8, eyeColor)
            }
            else -> {
                // IDLE_CHILL / IDLE_CURIOUS: sorrisinho sereno simétrico
                setSymmetric(7, 8, eyeColor)
            }
        }

        return pixels
    }

    private fun fnv1a64(input: String): Long {
        var hash = -0x340d631b7bdddcdbL
        val prime = 0x100000001b3L
        for (ch in input) {
            hash = hash xor ch.code.toLong()
            hash *= prime
        }
        return hash
    }

    private fun positiveMod(value: Long, mod: Int): Int {
        val m = value % mod
        return (if (m < 0) m + mod else m).toInt()
    }

    internal fun hslToArgb(h: Float, s: Float, l: Float): Int {
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
