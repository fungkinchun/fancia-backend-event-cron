package com.fancia.backend.event

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

class LambdaHandler : RequestHandler<Map<String, Any?>, Map<String, Any?>> {
    override fun handleRequest(input: Map<String, Any?>, context: Context): Map<String, Any?> {
        val applicationContext = SpringApplicationBuilder(EventCronApplication::class.java)
            .web(WebApplicationType.NONE)
            .run()
        val exitCode = SpringApplication.exit(applicationContext)
        if (exitCode != 0) {
            throw IllegalStateException("event-cron finished with exit code $exitCode")
        }
        return mapOf("status" to "ok", "exitCode" to exitCode)
    }
}
