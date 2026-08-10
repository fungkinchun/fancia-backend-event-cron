package com.fancia.backend.event.core.service

import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.entity.EventParticipant
import com.fancia.backend.shared.event.core.entity.EventParticipantId
import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.shared.event.core.support.RecurringEventVisibility
import com.fancia.backend.shared.event.core.enums.EventRole
import com.fancia.backend.shared.event.core.enums.OccurrenceStatus
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
class EventOccurrenceService(
    private val eventOccurrenceRepository: EventOccurrenceRepository,
) {
    /**
     * Materialises upcoming recurrence slots into the DB up to [DEFAULT_HORIZON_WEEKS].
     * Intended to run from the event-cron Lambda, not on API read paths.
     */
    @Transactional
    fun ensureUpcomingOccurrences(event: Event, now: LocalDateTime): Int {
        if (event.recurrenceFrequency == RecurrenceFrequency.NONE) return 0

        val horizon = now.plusWeeks(DEFAULT_HORIZON_WEEKS)
        var cursor = now
        var generated = 0
        while (generated < MAX_GENERATED_PER_CALL) {
            val nextStart = RecurringEventVisibility.nextOccurrenceStart(event, cursor) ?: break
            if (nextStart.isAfter(horizon)) break

            val nextEnd = RecurringEventVisibility.nextOccurrenceEnd(event, cursor)
                ?: nextStart.plus(
                    Duration.between(event.startTime ?: nextStart, event.endTime ?: nextStart.plusHours(1)),
                )

            if (!eventOccurrenceRepository.existsByEventIdAndStartTime(event.id!!, nextStart)) {
                val occurrence = EventOccurrence().apply {
                    this.event = event
                    this.startTime = nextStart
                    this.endTime = nextEnd
                    this.status = OccurrenceStatus.SCHEDULED
                    this.createdBy = event.createdBy
                }
                event.occurrences.add(occurrence)
                val saved = eventOccurrenceRepository.save(occurrence)
                copyHostsFromFirstOccurrence(event, saved)
                generated++
            }

            cursor = nextStart.plusSeconds(1)
        }
        return generated
    }

    private fun addHostParticipant(occurrence: EventOccurrence, hostUserId: java.util.UUID) {
        val occurrenceId = occurrence.id ?: return
        if (occurrence.participants.any { it.id.userId == hostUserId }) return
        val participant = EventParticipant(
            EventParticipantId(
                occurrenceId = occurrenceId,
                userId = hostUserId,
            ),
        )
        participant.occurrence = occurrence
        participant.role = EventRole.HOST
        occurrence.participants.add(participant)
    }

    private fun copyHostsFromFirstOccurrence(event: Event, occurrence: EventOccurrence) {
        val firstOccurrence = eventOccurrenceRepository.findFirstByEventIdAndStatusOrderByStartTimeAsc(event.id!!)
            ?: return
        for (existing in firstOccurrence.participants.filter {
            it.role == EventRole.HOST || it.role == EventRole.COHOST
        }) {
            addHostParticipant(occurrence, existing.id.userId)
        }
    }

    companion object {
        private const val DEFAULT_HORIZON_WEEKS = 8L
        private const val MAX_GENERATED_PER_CALL = 52
    }
}
