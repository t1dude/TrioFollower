package com.trionsandroid.app.data.nightscout

import java.time.Instant

/** The loop's "reason" text for one determination. */
data class Reasoning(
    /** When Nightscout received the determination, shortly after its glucose reading. */
    val timestamp: Instant,
    val text: String,
)
