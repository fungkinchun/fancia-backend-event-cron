package com.fancia.backend.event.core.exception

import com.fancia.backend.shared.common.core.exception.DomainException
import java.util.UUID

/**
 * Raised when upcoming occurrence materialisation fails for a single recurring event.
 */
class OccurrenceGenerationFailedException(
    val eventId: UUID,
    cause: Throwable? = null,
    title: String = "Occurrence Generation Failed",
    message: String = buildMessage(eventId, cause),
    errorCode: String = "OCCURRENCE_GENERATION_FAILED",
) : DomainException(title, message, errorCode) {
    init {
        if (cause != null) {
            initCause(cause)
        }
    }

    val rootExceptionType: String
        get() = (cause ?: this)::class.java.name

    companion object {
        private fun buildMessage(eventId: UUID, cause: Throwable?): String {
            val causeType = cause?.let { it::class.java.name }
            val causeMessage = cause?.message
            return buildString {
                append("Failed to generate upcoming occurrences for event $eventId")
                if (causeType != null) {
                    append(": $causeType")
                }
                if (!causeMessage.isNullOrBlank()) {
                    append(" — $causeMessage")
                }
            }
        }
    }
}

data class OccurrenceGenerationFailure(
    val eventId: UUID,
    val exceptionType: String,
    val errorCode: String?,
    val message: String?,
)

/**
 * Raised when the cron job finished with one or more per-event failures.
 */
class OccurrenceGenerationJobFailedException(
    val failures: List<OccurrenceGenerationFailure>,
    title: String = "Occurrence Generation Job Failed",
    message: String = buildMessage(failures),
    errorCode: String = "OCCURRENCE_GENERATION_JOB_FAILED",
) : DomainException(title, message, errorCode) {
    val failedEventIds: List<UUID>
        get() = failures.map { it.eventId }

    companion object {
        private fun buildMessage(failures: List<OccurrenceGenerationFailure>): String {
            val eventIds = failures.map { it.eventId }.joinToString(prefix = "[", postfix = "]")
            val details = failures.joinToString(separator = "; ") { failure ->
                buildString {
                    append(failure.eventId)
                    append("→")
                    append(failure.exceptionType)
                    if (!failure.errorCode.isNullOrBlank()) {
                        append(" (")
                        append(failure.errorCode)
                        append(")")
                    }
                    if (!failure.message.isNullOrBlank()) {
                        append(": ")
                        append(failure.message)
                    }
                }
            }
            return "Occurrence generation failed for ${failures.size} event(s) $eventIds: $details"
        }
    }
}
