package com.fancia.backend.event.core.repository

import com.fancia.backend.shared.event.core.entity.EventOccurrence
import com.fancia.backend.shared.event.core.enums.OccurrenceStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface EventOccurrenceRepository : JpaRepository<EventOccurrence, UUID> {
    fun existsByEventIdAndStartTime(eventId: UUID, startTime: LocalDateTime): Boolean

    @EntityGraph(attributePaths = ["participants"])
    fun findFirstByEventIdAndStatusOrderByStartTimeAsc(
        eventId: UUID,
        status: OccurrenceStatus = OccurrenceStatus.SCHEDULED,
    ): EventOccurrence?
}
