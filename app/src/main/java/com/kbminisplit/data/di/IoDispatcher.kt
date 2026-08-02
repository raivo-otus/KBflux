package com.kbminisplit.data.di

import javax.inject.Qualifier

/**
 * The dispatcher for genuinely blocking work — file IO and Room's non-suspending
 * calls. Injected rather than referenced directly so tests can substitute their
 * own and stay deterministic, the same reason [java.time.Clock] is injected.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
