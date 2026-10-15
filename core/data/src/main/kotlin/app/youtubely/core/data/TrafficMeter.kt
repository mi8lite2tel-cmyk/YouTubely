// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import app.youtubely.core.database.TrafficDao
import app.youtubely.core.database.TrafficDay
import app.youtubely.core.database.TrafficSum
import app.youtubely.core.datastore.PlaybackSettingsRepository
import app.youtubely.core.model.DayTraffic
import app.youtubely.core.model.TrafficKind
import app.youtubely.core.model.TrafficSummary
import app.youtubely.core.network.TrafficSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts the bytes of every HTTP response the app reads and keeps daily totals per network type
 * and kind. Bytes collect in memory and are written every few seconds. Nothing leaves the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TrafficMeter
    @Inject
    constructor(
        private val dao: TrafficDao,
        private val network: NetworkMonitor,
        private val settings: PlaybackSettingsRepository,
    ) : TrafficSink {
        private data class Key(
            val metered: Boolean,
            val kind: TrafficKind,
        )

        private val pending = ConcurrentHashMap<Key, AtomicLong>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            scope.launch {
                runCatching { dao.deleteBefore(today().toEpochDay() - RETENTION_DAYS) }
                while (isActive) {
                    delay(FLUSH_INTERVAL_MS)
                    flush()
                }
            }
        }

        override fun onBytes(
            kind: TrafficKind,
            bytes: Long,
        ) {
            pending.computeIfAbsent(Key(network.isMetered, kind)) { AtomicLong() }.addAndGet(bytes)
        }

        /** Writes what was counted since the last call; bytes that could not be written wait for the next one. */
        suspend fun flush() {
            val day = today().toEpochDay()
            for ((key, counter) in pending) {
                val bytes = counter.getAndSet(0)
                if (bytes <= 0) continue
                try {
                    dao.add(day, key.metered, key.kind.name, bytes)
                } catch (e: CancellationException) {
                    counter.addAndGet(bytes)
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") _: Exception,
                ) {
                    // A busy or broken database must never take the app down for a statistic.
                    counter.addAndGet(bytes)
                }
            }
        }

        /** Used mobile data this month, in bytes, updated live. */
        val monthMobileBytes: Flow<Long> = monthStarts().flatMapLatest { dao.observeMeteredSince(it.toEpochDay()) }

        /** Share of the monthly mobile allowance used; `null` when no allowance is set. */
        val monthlyUsedFraction: Flow<Float?> =
            combine(monthMobileBytes, settings.settings.map { it.monthlyMobileLimitMb }) { used, limitMb ->
                if (limitMb <= 0) null else used.toFloat() / (limitMb * BYTES_PER_MB)
            }

        val summary: Flow<TrafficSummary> =
            monthStarts().flatMapLatest { monthStart ->
                val from = minOf(monthStart, today().minusDays(DAYS_SHOWN - 1L)).toEpochDay()
                combine(dao.observeSums(monthStart.toEpochDay()), dao.observeDays(from)) { sums, days ->
                    summarize(sums, days)
                }
            }

        suspend fun reset() {
            pending.clear()
            dao.clear()
        }

        internal fun summarize(
            sums: List<TrafficSum>,
            days: List<TrafficDay>,
            today: LocalDate = today(),
        ): TrafficSummary {
            val byDay = days.groupBy { it.day }
            val shown =
                (DAYS_SHOWN - 1 downTo 0).map { back ->
                    val date = today.minusDays(back.toLong())
                    val rows = byDay[date.toEpochDay()].orEmpty()
                    DayTraffic(
                        date = date,
                        mobile = rows.filter { it.metered }.sumOf { it.bytes },
                        wifi = rows.filterNot { it.metered }.sumOf { it.bytes },
                    )
                }
            return TrafficSummary(
                monthMobile = sums.filter { it.metered }.sumOf { it.bytes },
                monthWifi = sums.filterNot { it.metered }.sumOf { it.bytes },
                monthByKind =
                    sums
                        .groupBy { runCatching { TrafficKind.valueOf(it.kind) }.getOrDefault(TrafficKind.Other) }
                        .mapValues { (_, rows) -> rows.sumOf { it.bytes } },
                days = shown,
            )
        }

        private fun monthStarts(): Flow<LocalDate> =
            flow {
                while (true) {
                    emit(today().withDayOfMonth(1))
                    delay(MONTH_CHECK_MS)
                }
            }

        private fun today(): LocalDate = LocalDate.now()

        private companion object {
            const val FLUSH_INTERVAL_MS = 5_000L
            const val MONTH_CHECK_MS = 3_600_000L
            const val RETENTION_DAYS = 400L
            const val DAYS_SHOWN = 14
            const val BYTES_PER_MB = 1024L * 1024
        }
    }
