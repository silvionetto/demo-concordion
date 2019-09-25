package com.silvionetto.concordion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoConcordionApplication

fun main(args: Array<String>) {
	runApplication<DemoConcordionApplication>(*args)
}
