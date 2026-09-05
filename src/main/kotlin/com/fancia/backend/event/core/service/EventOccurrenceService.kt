package com.fancia.backend.event.core.service

import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.entity.EventParticipant
import com.fancia.backend.shared.event.core.entity.EventParticipantId
import com.fancia.backend.event.core.repository.EventOccurrenceRepository
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.shared.event.core.support.EventTimeSlotSchedule
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
    @Transactional
    fun ensureUpcomingOccurrences(event: Event, now: LocalDateTime): Int {
        if (event.recurrenceFrequency == RecurrenceFrequency.NONE) return 0
        val eventId = event.id ?: return 0
        val live = eventRepository.findById(eventId).orElse(null) ?: return 0
        val anchors = EventTimeSlotSchedule.anchors(live)
        if (anchors.isEmpty()) return 0
        val maxFuture = maxFutureOccurrences(live.recurrenceFrequency)

        var generated = 0
        for (anchor in anchors) {
            var cursor = now
            var futureSlots = 0
            while (futureSlots < maxFuture) {
                val nextStart = RecurringEventVisibility.nextOccurrenceStartForAnchor(
                    live,
                    anchor.startTime,
                    cursor,
                ) ?: break

                val nextEnd = RecurringEventVisibility.nextOccurrenceEndForAnchor(
                    live,
                    anchor.startTime,
                    anchor.endTime,
                    cursor,
                ) ?: nextStart.plus(Duration.between(anchor.startTime, anchor.endTime))

                if (!eventOccurrenceRepository.existsByEventIdAndStartTime(eventId, nextStart)) {
                    val slot = live.timeSlots.firstOrNull { it.id == anchor.id }
                    val occurrence = EventOccurrence().apply {
                        this.event = eventRepository.getReferenceById(eventId)
                        this.timeSlot = slot
                        this.startTime = nextStart
                        this.endTime = nextEnd
                        this.status = OccurrenceStatus.SCHEDULED
                        this.createdBy = live.createdBy
                    }
                    val saved = eventOccurrenceRepository.save(occurrence)
                    copyHostsFromFirstOccurrence(eventId, saved)
                    generated++
                }

                futureSlots++
                cursor = nextStart.plusSeconds(1)
            }
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
