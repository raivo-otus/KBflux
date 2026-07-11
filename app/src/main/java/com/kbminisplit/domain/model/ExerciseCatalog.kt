package com.kbminisplit.domain.model

object ExerciseCatalog {
    // KB flow movements — displayed as reference rows on the Tracker (rep schemes
    // come from spec §2.2). They are *not* individually tracked: a session
    // records 3 circuit completions under [KbFlow] below, not per-movement
    // sets. Which three appear depends on the day's split ([kbFlowForSplit]).
    val Swings = Exercise("swings", "Swings", Category.KB, isPerSide = false, weightStepKg = 2.0)
    val CleanAndPress = Exercise("clean_and_press", "Clean & Press", Category.KB, isPerSide = true, weightStepKg = 2.0)
    val GobletSquat = Exercise("goblet_squat", "Goblet Squat", Category.KB, isPerSide = false, weightStepKg = 2.0)
    val HighPull = Exercise("high_pull", "High Pull", Category.KB, isPerSide = true, weightStepKg = 2.0)
    val Snatch = Exercise("snatch", "Snatch", Category.KB, isPerSide = true, weightStepKg = 2.0)

    // Sentinel KB-flow exercise. One `set_entry` row per completed circuit
    // (3 per session) uses this slug. KB progression is single-weight (§9.3),
    // and the Progression chart for KB is one line (§6.1), so per-movement
    // rows would add storage without surfacing anywhere.
    val KbFlow = Exercise("kb_flow", "KB Flow", Category.KB, isPerSide = false, weightStepKg = 2.0)

    val LatPulldown = Exercise("lat_pulldown", "Lat Pulldown", Category.A, isPerSide = false, weightStepKg = 2.5)
    val BarbellRow = Exercise("barbell_row", "Barbell Row", Category.A, isPerSide = false, weightStepKg = 2.5)
    val Bench = Exercise("bench", "Bench Press", Category.B, isPerSide = false, weightStepKg = 2.5)
    val Ohp = Exercise("ohp", "Overhead Press", Category.B, isPerSide = false, weightStepKg = 2.5)

    // Assisted Dips replaced OHP as Split B's second push movement. The logged
    // number is machine assistance (pin weight) subtracted from bodyweight, so it
    // uses inverted double progression (§ assisted). Not onboarded — carries a
    // [Exercise.defaultStartingWeightKg] assist fallback until the first session,
    // adjustable via the Tracker's weight editor to match the user's machine.
    val AssistedDip = Exercise(
        slug = "assisted_dip",
        displayName = "Assisted Dips",
        category = Category.B,
        isPerSide = false,
        weightStepKg = 2.5,
        defaultStartingWeightKg = 40.0,
        mechanic = ExerciseMechanic.ASSISTED,
    )
    val HighBarSquat = Exercise("high_bar_squat", "High-Bar Squat", Category.C, isPerSide = false, weightStepKg = 2.5)
    val RomanianDeadlift = Exercise(
        slug = "romanian_deadlift",
        displayName = "Romanian Deadlift (RDL)",
        category = Category.C,
        isPerSide = false,
        weightStepKg = 2.5,
    )

    // Auxiliary movements — optional accessory work done after the main workout
    // (§ aux). They follow the same double progression as the strength lifts but
    // are not part of onboarding, so each carries a [Exercise.defaultStartingWeightKg]
    // fallback used until the movement's first session is logged.
    val SideDeltFly = Exercise(
        slug = "side_delt_fly",
        displayName = "Side-Delt Flyes",
        category = Category.AUX,
        isPerSide = false,
        weightStepKg = 2.0,
        defaultStartingWeightKg = 6.0,
    )
    val TricepExtension = Exercise(
        slug = "tricep_extension",
        displayName = "Tricep Extensions",
        category = Category.AUX,
        isPerSide = false,
        weightStepKg = 2.0,
        defaultStartingWeightKg = 10.0,
    )
    val BackExtension = Exercise(
        slug = "back_extension",
        displayName = "Back Extensions",
        category = Category.AUX,
        isPerSide = false,
        weightStepKg = 2.0,
        defaultStartingWeightKg = 0.0,
    )
    val BicepCurl = Exercise(
        slug = "bicep_curl",
        displayName = "Bicep Curls",
        category = Category.AUX,
        isPerSide = false,
        weightStepKg = 2.0,
        defaultStartingWeightKg = 10.0,
    )

    val all: List<Exercise> = listOf(
        Swings, CleanAndPress, GobletSquat, HighPull, Snatch, KbFlow,
        // Ohp is retained (no longer prescribed) so historical OHP sessions still
        // resolve via bySlug; AssistedDip is its Split B replacement.
        LatPulldown, BarbellRow, Bench, Ohp, AssistedDip, HighBarSquat, RomanianDeadlift,
        // Aux appended last so LogViewModel's CATALOG_ORDER renders them after
        // the main movements in the session-detail sheet.
        SideDeltFly, TricepExtension, BackExtension, BicepCurl,
    )

    /**
     * The day's KB flow movements, themed to the split (spec §2.2). Reference
     * labels for the KB section header — not used for set tracking. Reps are
     * positional (32/16/8 at full scheme) regardless of which movements appear.
     */
    fun kbFlowForSplit(split: Split): List<Exercise> = when (split) {
        Split.A -> listOf(Swings, HighPull, GobletSquat)
        Split.B -> listOf(Swings, CleanAndPress, GobletSquat)
        Split.C -> listOf(Swings, GobletSquat, Snatch)
    }

    fun bySlug(slug: String): Exercise? = all.firstOrNull { it.slug == slug }

    fun strengthForSplit(split: Split): Pair<Exercise, Exercise> = when (split) {
        Split.A -> LatPulldown to BarbellRow
        Split.B -> Bench to AssistedDip
        Split.C -> HighBarSquat to RomanianDeadlift
    }

    /** Auxiliary movements offered after the main workout for a given split. */
    fun auxForSplit(split: Split): List<Exercise> = when (split) {
        Split.A -> listOf(SideDeltFly, TricepExtension, BackExtension)
        Split.B -> listOf(SideDeltFly, BicepCurl, BackExtension)
        Split.C -> listOf(SideDeltFly, TricepExtension, BicepCurl)
    }
}
