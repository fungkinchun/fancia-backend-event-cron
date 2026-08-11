package com.fancia.backend.event.core.job

import com.fancia.backend.event.core.exception.OccurrenceGenerationFailedException
import com.fancia.backend.event.core.exception.OccurrenceGenerationFailure
import com.fancia.backend.event.core.exception.OccurrenceGenerationJobFailedException
import com.fancia.backend.event.core.repository.EventRepository
import com.fancia.backend.event.core.service.EventOccurrenceService
import com.fancia.backend.shared.common.core.exception.DomainException
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

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
        val failures = mutableListOf<OccurrenceGenerationFailure>()

        for (event in recurring) {
            val eventId = event.id
            if (eventId == null) {
                log.warn("Skipping recurring event with null id")
                continue
            }
            try {
                val created = eventOccurrenceService.ensureUpcomingOccurrences(event, now)
                if (created > 0) {
                    eventsTouched++
                    occurrencesCreated += created
                }
            } catch (ex: Exception) {
                failures += recordFailure(eventId, ex)
            }
        }

        log.info(
            "Finished occurrence generation: created={} across eventsTouched={}, scanned={}, failedEventIds={}",
            occurrencesCreated,
            eventsTouched,
            recurring.size,
            failures.map { it.eventId },
        )

        if (failures.isNotEmpty()) {
            log.error(
                "Occurrence generation failures by event: {}",
                failures.joinToString(separator = " | ") {
                    "eventId=${it.eventId} exceptionType=${it.exceptionType} errorCode=${it.errorCode} message=${it.message}"
                },
            )
            throw OccurrenceGenerationJobFailedException(failures)
        }
    }

    private fun recordFailure(eventId: UUID, ex: Exception): OccurrenceGenerationFailure {
        val domainEx = when (ex) {
            is DomainException -> ex
            else -> OccurrenceGenerationFailedException(eventId = eventId, cause = ex)
        }
        val rootType = when (domainEx) {
            is OccurrenceGenerationFailedException -> domainEx.rootExceptionType
            else -> domainEx::class.java.name
        }

        log.error(
            "Failed to generate occurrences for eventId={} exceptionType={} errorCode={} message={}",
            eventId,
            rootType,
            domainEx.errorCode,
            domainEx.message,
            domainEx,
        )

        return OccurrenceGenerationFailure(
            eventId = eventId,
            exceptionType = rootType,
            errorCode = domainEx.errorCode,
            message = domainEx.message,
        )
    }
}
