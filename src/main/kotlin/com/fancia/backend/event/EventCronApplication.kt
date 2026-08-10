package com.fancia.backend.event

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import kotlin.system.exitProcess

@EntityScan(
    basePackages = [
        "com.fancia.backend.shared.event.core.entity",
        "com.fancia.backend.shared.common.core.entity",
    ],
)
@EnableJpaRepositories(
    basePackages = [
        "com.fancia.backend.event.core.repository",
    ],
)
@SpringBootApplication(scanBasePackages = ["com.fancia.backend.event"])
class EventCronApplication

fun main(args: Array<String>) {
    val context = runApplication<EventCronApplication>(*args)
    exitProcess(SpringApplication.exit(context))
}
