package com.silvionetto.concordion

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
class DemoConcordionApplicationTests {

    @Autowired
    lateinit var myComponent: MyComponent

    @Test
    fun contextLoads() {
        assertEquals("Hello World", myComponent.helloWorld())
    }

}
