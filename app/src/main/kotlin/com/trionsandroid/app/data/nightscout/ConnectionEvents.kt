package com.trionsandroid.app.data.nightscout

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets Settings tell the rest of the app that a Nightscout connection was just verified, so Home
 * can drop its stale "set up Nightscout" error and start a sync right away instead of waiting for
 * the next refresh.
 */
@Singleton
class ConnectionEvents @Inject constructor() {
    private val _established = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val established: SharedFlow<Unit> = _established

    fun notifyEstablished() {
        _established.tryEmit(Unit)
    }
}
