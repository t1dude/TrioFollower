package com.trionsandroid.app.data.nightscout

import java.time.Instant

/** The loop's "reason" text for one determination, as uploaded to Nightscout's devicestatus. */
data class Reasoning(
    /** When Nightscout received this determination — a little after the glucose reading it was
     *  computed for. */
    val timestamp: Instant,
    val text: String,
)
