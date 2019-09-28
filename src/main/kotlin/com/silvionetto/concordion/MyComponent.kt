package com.silvionetto.concordion

import org.springframework.context.annotation.PropertySource
import org.springframework.stereotype.Component

@Component
@PropertySource("classpath:application.properties")
class MyComponent {

    fun helloWorld(): String {
        return "Hello World"
    }

    fun helloWorld(name: String): String {
        return "${helloWorld()}  $name"
    }
}