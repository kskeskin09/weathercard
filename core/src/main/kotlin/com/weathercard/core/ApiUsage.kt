package com.weathercard.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

enum class RequestKind { FULL_FORECAST, SIX_HOURS, GEOCODING }

/** Integer tenths avoid accumulating floating-point errors in weighted quota estimates. */
@Serializable
data class UsageTotals(val full: Long = 0, val partial: Long = 0, val searches: Long = 0, val quotaTenths: Long = 0) {
    val weatherRequests: Long get() = full + partial
    operator fun plus(other: UsageTotals) = UsageTotals(full + other.full, partial + other.partial, searches + other.searches, quotaTenths + other.quotaTenths)
    fun add(kind: RequestKind) = this + when (kind) {
        RequestKind.FULL_FORECAST -> UsageTotals(full = 1, quotaTenths = FULL_COST_TENTHS)
        RequestKind.SIX_HOURS -> UsageTotals(partial = 1, quotaTenths = PARTIAL_COST_TENTHS)
        RequestKind.GEOCODING -> UsageTotals(searches = 1)
    }
    companion object {
        // One coordinate, best_match, seven days: 14 hourly + 9 daily fields.
        const val FULL_COST_TENTHS = 23L
        const val PARTIAL_COST_TENTHS = 14L
    }
}

@Serializable
data class UsageBucket(val hour: Long, val totals: UsageTotals)

@Serializable
data class UsageLedger(
    val startedAt: Long,
    val lifetime: UsageTotals = UsageTotals(),
    val buckets: List<UsageBucket> = emptyList(),
)

data class UsageSummary(
    val startedAt: Long,
    val hour: UsageTotals,
    val day: UsageTotals,
    val month: UsageTotals,
    val lifetime: UsageTotals,
)

interface UsageStore {
    suspend fun read(): UsageLedger?
    suspend fun write(ledger: UsageLedger)
}

class UsageTracker(private val store: UsageStore, private val clock: Clock = Clock.systemUTC()) {
    private val mutex = Mutex()

    /** Record before dispatch; even failed/offline attempts are deliberately counted conservatively. */
    suspend fun record(kind: RequestKind) = mutex.withLock {
        val now = clock.instant()
        val hour = now.truncatedTo(ChronoUnit.HOURS).epochSecond
        val month = now.atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toEpochSecond()
        val ledger = store.read() ?: UsageLedger(now.epochSecond)
        val buckets = ledger.buckets.filter { it.hour >= month && it.hour != hour } +
            UsageBucket(hour, (ledger.buckets.find { it.hour == hour }?.totals ?: UsageTotals()).add(kind))
        store.write(ledger.copy(lifetime = ledger.lifetime.add(kind), buckets = buckets.sortedBy { it.hour }))
    }

    suspend fun summary(): UsageSummary = mutex.withLock {
        val now = clock.instant()
        val hour = now.truncatedTo(ChronoUnit.HOURS).epochSecond
        val day = now.truncatedTo(ChronoUnit.DAYS).epochSecond
        val month = now.atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toEpochSecond()
        val ledger = store.read() ?: UsageLedger(now.epochSecond)
        fun sum(start: Long) = ledger.buckets.filter { it.hour in start..hour }.fold(UsageTotals()) { total, bucket -> total + bucket.totals }
        UsageSummary(ledger.startedAt, sum(hour), sum(day), sum(month), ledger.lifetime)
    }
}

