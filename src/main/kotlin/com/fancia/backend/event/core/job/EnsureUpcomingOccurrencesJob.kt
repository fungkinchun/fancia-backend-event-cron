package com.fancia.backend.event.core.job

import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.service.EventOccurrenceService
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Cron entrypoint: materialise upcoming occurrence rows for all recurring events, then exit.
 * Invoked by EventBridge → Lambda (Spring Boot run-once).
 */
@Component
class EnsureUpcomingOccurrencesJob(
    private val eventRepository: EventRepository,
    private val eventOccurrenceService: EventOccurrenceService,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val now = LocalDateTime.now()
        val recurring = eventRepository.findByRecurrenceFrequencyNot(RecurrenceFrequency.NONE)
        log.info("Starting upcoming occurrence generation for {} recurring events at {}", recurring.size, now)

        var eventsTouched = 0
        var occurrencesCreated = 0
        var failures = 0

        for (event in recurring) {
            try {
                val created = eventOccurrenceService.ensureUpcomingOccurrences(event, now)
                if (created > 0) {
                    eventsTouched++
                    occurrencesCreated += created
                }
            } catch (ex: Exception) {
                failures++
                log.error("Failed to generate occurrences for event {}", event.id, ex)
            }
        }

        log.info(
            "Finished occurrence generation: created={} across eventsTouched={}, scanned={}, failures={}",
            occurrencesCreated,
            eventsTouched,
            recurring.size,
            failures,
        )
        if (failures > 0) {
            throw IllegalStateException("Occurrence generation failed for $failures event(s)")
        }
    }
}
