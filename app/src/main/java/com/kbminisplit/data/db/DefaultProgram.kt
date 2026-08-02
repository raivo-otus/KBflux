package com.kbminisplit.data.db

import com.kbminisplit.domain.model.GroupKind

/**
 * The program every install starts from — a literal transcription of the three-day
 * kettlebell + barbell split that used to be hardcoded in `ExerciseCatalog`.
 *
 * Two details are load-bearing for upgrades:
 *  - Day keys stay `"A"`, `"B"`, `"C"`, matching the values already written to
 *    `session.split`, so existing history keeps resolving to a day.
 *  - The circuit groups reuse the `kb_flow` sentinel slug that existing circuit
 *    rows in `set_entry` were logged under.
 *
 * Two things deliberately differ from the old hardcoded behaviour:
 *  - Reps are ranges rather than a single auto-incrementing number. The circuit
 *    ranges span the old four-stage rep ramp (20→32, 10→16, 5→8), so the ramp's
 *    purpose — easing into a heavier bell — is served by starting at the low end.
 *  - Accessories seed with no lead-in sets. Priming and warming up a 6 kg flye was
 *    an artefact of the old category-based floor; the `leadInSets` field replaces it.
 */
internal data class SeedItem(
    val slug: String,
    val name: String,
    val minReps: Int,
    val maxReps: Int,
    val weightKg: Double,
    val weightStepKg: Double = 2.5,
    val isPerSide: Boolean = false,
    val isAssisted: Boolean = false,
    val sets: Int = 3,
    val leadInSets: Int = 2,
)

internal data class SeedGroup(
    val name: String,
    val kind: GroupKind,
    val rotates: Boolean,
    val isDeferred: Boolean = false,
    val rounds: Int = 3,
    val circuitSlug: String? = null,
    val usesLadder: Boolean = false,
    val items: List<SeedItem>,
)

internal data class SeedDay(
    val key: String,
    val name: String,
    val groups: List<SeedGroup>,
)

/** Sentinel slug the kettlebell circuit's round rows have always been stored under. */
internal const val KB_FLOW_SLUG = "kb_flow"

internal const val DEFAULT_CIRCUIT_WEIGHT_KG = 16.0
internal const val DEFAULT_MAX_REPS = 12
internal const val DEFAULT_MIN_REPS = 8

private const val KB_STEP = 2.0
private const val AUX_STEP = 2.0

private fun circuit(vararg items: SeedItem) = SeedGroup(
    name = "Kettlebell flow",
    kind = GroupKind.CIRCUIT,
    rotates = false,
    rounds = 3,
    circuitSlug = KB_FLOW_SLUG,
    usesLadder = true,
    items = items.toList(),
)

private fun main(vararg items: SeedItem) = SeedGroup(
    name = "Main",
    kind = GroupKind.STANDARD,
    rotates = true,
    items = items.toList(),
)

private fun accessories(vararg items: SeedItem) = SeedGroup(
    name = "Accessories",
    kind = GroupKind.STANDARD,
    rotates = true,
    isDeferred = true,
    items = items.toList(),
)

/** A circuit movement: reps are a label, weight comes from the group. */
private fun kb(slug: String, name: String, min: Int, max: Int, perSide: Boolean = false) =
    SeedItem(
        slug = slug,
        name = name,
        minReps = min,
        maxReps = max,
        weightKg = DEFAULT_CIRCUIT_WEIGHT_KG,
        weightStepKg = KB_STEP,
        isPerSide = perSide,
        sets = 0,
        leadInSets = 0,
    )

private fun lift(slug: String, name: String, weightKg: Double, assisted: Boolean = false) =
    SeedItem(
        slug = slug,
        name = name,
        minReps = DEFAULT_MIN_REPS,
        maxReps = DEFAULT_MAX_REPS,
        weightKg = weightKg,
        weightStepKg = 2.5,
        isAssisted = assisted,
        leadInSets = 2,
    )

private fun accessory(slug: String, name: String, weightKg: Double) =
    SeedItem(
        slug = slug,
        name = name,
        minReps = DEFAULT_MIN_REPS,
        maxReps = DEFAULT_MAX_REPS,
        weightKg = weightKg,
        weightStepKg = AUX_STEP,
        leadInSets = 0,
    )

private val Swings = kb("swings", "Swings", 20, 32)
private val GobletSquatLead = kb("goblet_squat", "Goblet Squat", 10, 16)
private val GobletSquatTail = kb("goblet_squat", "Goblet Squat", 5, 8)
private val HighPull = kb("high_pull", "High Pull", 10, 16, perSide = true)
private val CleanAndPress = kb("clean_and_press", "Clean & Press", 10, 16, perSide = true)
private val Snatch = kb("snatch", "Snatch", 5, 8, perSide = true)

internal val DEFAULT_PROGRAM: List<SeedDay> = listOf(
    SeedDay(
        key = "A",
        name = "Pull",
        groups = listOf(
            circuit(Swings, HighPull, GobletSquatTail),
            main(
                lift("lat_pulldown", "Lat Pulldown", 35.0),
                lift("barbell_row", "Barbell Row", 30.0),
            ),
            accessories(
                accessory("side_delt_fly", "Side-Delt Flyes", 6.0),
                accessory("tricep_extension", "Tricep Extensions", 10.0),
                accessory("back_extension", "Back Extensions", 0.0),
            ),
        ),
    ),
    SeedDay(
        key = "B",
        name = "Push",
        groups = listOf(
            circuit(Swings, CleanAndPress, GobletSquatTail),
            main(
                lift("bench", "Bench Press", 40.0),
                lift("assisted_dip", "Assisted Dips", 40.0, assisted = true),
            ),
            accessories(
                accessory("side_delt_fly", "Side-Delt Flyes", 6.0),
                accessory("bicep_curl", "Bicep Curls", 10.0),
                accessory("back_extension", "Back Extensions", 0.0),
            ),
        ),
    ),
    SeedDay(
        key = "C",
        name = "Legs",
        groups = listOf(
            circuit(Swings, GobletSquatLead, Snatch),
            main(
                lift("high_bar_squat", "High-Bar Squat", 50.0),
                lift("romanian_deadlift", "Romanian Deadlift (RDL)", 60.0),
            ),
            accessories(
                accessory("side_delt_fly", "Side-Delt Flyes", 6.0),
                accessory("tricep_extension", "Tricep Extensions", 10.0),
                accessory("bicep_curl", "Bicep Curls", 10.0),
            ),
        ),
    ),
)

/**
 * Every slug the app has ever written to `set_entry`, so the registry can resolve
 * a name for any historical session. Includes `ohp`, which was replaced by
 * Assisted Dips and is no longer programmed, and the `kb_flow` sentinel.
 */
internal val REGISTRY_SEED: Map<String, String> = buildMap {
    put(KB_FLOW_SLUG, "Kettlebell flow")
    put("ohp", "Overhead Press")
    DEFAULT_PROGRAM.forEach { day ->
        day.groups.forEach { group -> group.items.forEach { put(it.slug, it.name) } }
    }
}
