package com.kbminisplit.domain.model

/**
 * How a movement's logged weight maps to physiological load, which flips the
 * direction of double progression.
 *
 *  - [TRADITIONAL]: logged weight *is* the load. Getting stronger means adding
 *    weight, so progression increments.
 *  - [ASSISTED]: the logged number is machine assistance (a pin weight that is
 *    *subtracted* from bodyweight). Getting stronger means needing *less*
 *    assistance, so progression decrements the pin toward zero.
 *
 * Derived from [ProgramItem.isAssisted] rather than stored, so the acclimatization
 * and effective-load helpers can take a mechanic without knowing about programs.
 */
enum class ExerciseMechanic { TRADITIONAL, ASSISTED }
