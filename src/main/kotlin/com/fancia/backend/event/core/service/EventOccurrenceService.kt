package com.fancia.backend.event.core.service

import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.entity.EventParticipant
import com.fancia.backend.shared.event.core.entity.EventParticipantId
import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.shared.event.core.support.RecurringEventVisibility
import com.fancia.backend.shared.event.core.enums.EventRole
import com.fancia.backend.shared.event.core.enums.OccurrenceStatus
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
class EventOccurrenceService(
    private val eventRepository: EventRepository,
    private val eventOccurrenceRepository: EventOccurrenceRepository,
) {
    /**
     * Ensures the next few future occurrence rows exist for a recurring event.
     * Caps: weekly = 3, monthly = 1, daily = 3. Skips slots that already exist.
     */
    @Transactional
    fun ensureUpcomingOccurrences(event: Event, now: LocalDateTime): Int {
        if (event.recurrenceFrequency == RecurrenceFrequency.NONE) return 0
        val eventId = event.id ?: return 0
        val maxFuture = maxFutureOccurrences(event.recurrenceFrequency)

        var cursor = now
        var futureSlots = 0
        var generated = 0
        while (futureSlots < maxFuture) {
            val nextStart = RecurringEventVisibility.nextOccurrenceStart(event, cursor) ?: break

            val nextEnd = RecurringEventVisibility.nextOccurrenceEnd(event, cursor)
                ?: nextStart.plus(
                    Duration.between(event.startTime ?: nextStart, event.endTime ?: nextStart.plusHours(1)),
                )

            if (!eventOccurrenceRepository.existsByEventIdAndStartTime(eventId, nextStart)) {
                val occurrence = EventOccurrence().apply {
                    this.event = eventRepository.getReferenceById(eventId)
                    this.startTime = nextStart
                    this.endTime = nextEnd
                    this.status = OccurrenceStatus.SCHEDULED
                    this.createdBy = event.createdBy
                }
                val saved = eventOccurrenceRepository.save(occurrence)
                copyHostsFromFirstOccurrence(eventId, saved)
                generated++
            }

            futureSlots++
            cursor = nextStart.plusSeconds(1)
        }
        return generated
    }

    private fun maxFutureOccurrences(frequency: RecurrenceFrequency): Int =
        when (frequency) {
            RecurrenceFrequency.WEEKLY -> MAX_FUTURE_WEEKLY
            RecurrenceFrequency.MONTHLY -> MAX_FUTURE_MONTHLY
            RecurrenceFrequency.DAILY -> MAX_FUTURE_DAILY
            RecurrenceFrequency.NONE -> 0
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

    private fun copyHostsFromFirstOccurrence(eventId: UUID, occurrence: EventOccurrence) {
        val firstOccurrence = eventOccurrenceRepository.findFirstByEventIdAndStatusOrderByStartTimeAsc(eventId)
            ?: return
        for (existing in firstOccurrence.participants.filter {
            it.role == EventRole.HOST || it.role == EventRole.COHOST
        }) {
            addHostParticipant(occurrence, existing.id.userId)
        }
    }

    companion object {
        private const val MAX_FUTURE_WEEKLY = 3
        private const val MAX_FUTURE_MONTHLY = 1
        private const val MAX_FUTURE_DAILY = 3
    }
}
