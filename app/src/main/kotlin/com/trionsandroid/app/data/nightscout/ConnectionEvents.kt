package com.trionsandroid.app.data.nightscout

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Lets Settings tell Home a Nightscout connection was verified, so it can clear its error and sync. */
@Singleton
class ConnectionEvents @Inject constructor() {
    private val _established = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val established: SharedFlow<Unit> = _established

    fun notifyEstablished() {
        _established.tryEmit(Unit)
    }
}
