package com.silvionetto.concordion

import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration


@RunWith(SpringConcordionRunner::class)
@ContextConfiguration(classes = arrayOf(DemoConcordionApplication::class, MyComponent::class))
class DemoConcordionApplicationTests {

    @Configuration
    @ComponentScan("com.silvionetto.concordion")
    class Config {

        @Bean
        fun component(): MyComponent {
            return MyComponent()
        }
    }

    @Autowired
    lateinit var myComponent: MyComponent

    fun greeting(): String {
        return myComponent.helloWorld()
    }

    fun greeting(name: String): String {
        return myComponent.helloWorld(name)
    }


}
