package com.beatforge.studio.audio

import java.util.UUID

/**
 * Starter kits. Rows follow [Templates.kinds]:
 * Kick, Snare, Clap, Hi-Hat, Open Hat, 808, Tom, Perc.  'x' = hit, '.' = rest.
 */
class Template(
    val name: String,
    val tagline: String,
    val bpm: Int,
    val swing: Float,
    val a: List<String>,
    val b: List<String>
)

object Templates {
    val kinds = listOf(
        DrumKind.KICK, DrumKind.SNARE, DrumKind.CLAP, DrumKind.HAT,
        DrumKind.OPEN_HAT, DrumKind.BASS808, DrumKind.TOM, DrumKind.PERC
    )

    private const val E = "................"

    val BOOM_BAP = Template(
        "Boom Bap", "Dusty, swung, classic", 90, 0.35f,
        a = listOf("x.....x..x......", "....x.......x...", E, "x.x.x.x.x.x.x.x.", "..............x.", E, E, E),
        b = listOf("x..x..x...x.x...", "....x.......x...", "....x.......x...", "x.xxx.x.x.xxx.x.", "......x.......x.", E, "............x.xx", "..x.....x.......")
    )
    val TRAP = Template(
        "Trap", "Rolling hats, heavy 808", 140, 0f,
        a = listOf("x......x..x.....", "........x.......", "........x.......", "x.x.x.x.x.x.x.x.", "......x.......x.", "x......x..x.....", E, "....x.......x..."),
        b = listOf("x...x..x..x..x..", "........x.......", "........x.......", "x.xxx.xxx.x.xxxx", "..............x.", "x...x..x..x..x..", "............xxx.", E)
    )
    val DRILL = Template(
        "Drill", "Sliding, dark, syncopated", 142, 0f,
        a = listOf("x.....x....x....", "........x.......", "........x.......", "x.x.xxx.x.x.xxx.", "......x......x..", "x.....x....x....", "..............xx", E),
        b = listOf("x.....x..x.x....", "........x.......", "........x...x...", "x.xxx.x.x.xxx.xx", "......x.......x.", "x.....x..x.x....", "............xxxx", "..x.....x.......")
    )
    val HOUSE = Template(
        "House", "Four on the floor", 124, 0f,
        a = listOf("x...x...x...x...", E, "....x.......x...", "x.x.x.x.x.x.x.x.", "..x...x...x...x.", "..x...x...x...x.", E, ".x..x..x.x..x..."),
        b = listOf("x...x...x...x...", E, "....x.......x...", "x.x.x.x.x.x.x.xx", "..x...x...x...x.", "..x...x.....x...", "...........x.x.x", ".x..x..x.x..x..x")
    )
    val BLANK = Template("Blank", "Start from silence", 100, 0f, List(8) { E }, List(8) { E })

    val all = listOf(BOOM_BAP, TRAP, DRILL, HOUSE, BLANK)

    private fun parse(row: String): List<Boolean> =
        List(STEPS) { row.getOrNull(it) == 'x' }

    private fun volumeFor(kind: DrumKind) = when (kind) {
        DrumKind.KICK -> 0.9f
        DrumKind.BASS808 -> 0.85f
        DrumKind.HAT -> 0.5f
        DrumKind.OPEN_HAT -> 0.45f
        DrumKind.PERC -> 0.6f
        DrumKind.TOM -> 0.7f
        else -> 0.8f
    }

    private fun panFor(kind: DrumKind) = when (kind) {
        DrumKind.HAT -> -0.15f
        DrumKind.OPEN_HAT -> 0.15f
        DrumKind.PERC -> 0.25f
        DrumKind.TOM -> -0.2f
        else -> 0f
    }

    fun create(template: Template): Project {
        val tracks = kinds.mapIndexed { i, kind ->
            Track(
                id = i,
                name = kind.label,
                kind = kind,
                colorIndex = i,
                volume = volumeFor(kind),
                pan = panFor(kind)
            )
        }
        val empty: Pattern = tracks.map { List(STEPS) { false } }
        val patA: Pattern = template.a.map(::parse)
        val patB: Pattern = template.b.map(::parse)
        return Project(
            id = UUID.randomUUID().toString(),
            name = if (template == BLANK) "New Beat" else "${template.name} Beat",
            bpm = template.bpm,
            swing = template.swing,
            tracks = tracks,
            patterns = listOf(patA, patB, empty, empty),
            arrangement = listOf(0, 0, 0, 1)
        )
    }
}
