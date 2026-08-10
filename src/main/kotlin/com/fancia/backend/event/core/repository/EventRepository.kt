package com.fancia.backend.event.core.repository

import com.fancia.backend.shared.event.core.entity.Event
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EventRepository : JpaRepository<Event, UUID> {
    fun findByRecurrenceFrequencyNot(frequency: RecurrenceFrequency): List<Event>
}
