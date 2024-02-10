/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.kotlin

import org.apache.camel.CamelExecutionException
import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.kotlin.components.direct
import org.apache.camel.kotlin.dataformats.csv
import org.apache.camel.kotlin.languages.body
import org.apache.camel.kotlin.languages.constant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EipsTest {

    private lateinit var ctx: DefaultCamelContext

    @BeforeEach
    fun beforeEach() {
        ctx = DefaultCamelContext()
    }

    @AfterEach
    fun afterEach() {
        ctx.stop()
    }

    @Test
    fun testOnException() {
        val producer = ctx.createProducerTemplate()
        val handled = AtomicBoolean(false)

        camel(ctx) {
            onException(Exception::class) {
                handled(true)
                outputs {
                    process { handled.set(true) }
                }
            }
            route {
                from { direct { name("input") } }
                steps {
                    throwException(Exception("some"))
                }
            }
        }
        ctx.start()

        producer.sendBody("direct:input", null)
        assertTrue(handled.get())
    }

    @Test
    fun testTryCatch() {
        val producer = ctx.createProducerTemplate()
        val exchange = AtomicReference<Exchange>()
        camel(ctx) {
            route {
                from { direct { name("first") } }
                steps {
                    doTry {
                        steps {
                            throwException(Exception("raised"))
                        }
                        doCatch(Exception::class) {
                            onWhen(body() contains constant("some"))
                            steps {
                                process { exchange.set(it) }
                            }
                        }
                    }
                }
            }
        }
        ctx.start()
        producer.sendBody("direct:first", "some")
        assertEquals("some", exchange.get().message.body)
    }

    @Test
    fun testUnmarshal() {
        val producer = ctx.createProducerTemplate()
        val exchange = AtomicReference<Exchange>()
        camel(ctx) {
            route {
                from { direct { name("first") } }
                steps {
                    unmarshal { csv {  } }
                    process { exchange.set(it) }
                }
            }
        }
        ctx.start()
        producer.sendBody("direct:first", "1,2,3,4")
        assertEquals("[[1, 2, 3, 4]]", exchange.get().message.body.toString())
    }

    @Test
    fun testMulticast() {
        val producer = ctx.createProducerTemplate()
        val exchanges = ConcurrentLinkedDeque<Exchange>()
        camel(ctx) {
            route {
                from { direct { name("first") } }
                steps {
                    multicast {
                        outputs {
                            process { exchanges.add(it) }
                            process { exchanges.add(it) }
                            process { exchanges.add(it) }
                        }
                    }
                }
            }
        }
        ctx.start()
        producer.sendBody("direct:first", null)
        assertEquals(3, exchanges.size)
    }

    @Test
    fun testValidate() {
        val producer = ctx.createProducerTemplate()
        val exchange = AtomicReference<Exchange>()
        camel(ctx) {
            route {
                from { direct { name("first") } }
                steps {
                    validate(body().toPredicate())
                    process { exchange.set(it) }
                }
            }
        }
        ctx.start()
        producer.sendBody("direct:first", true)
        assertNotNull(exchange.get())
        assertThrows<CamelExecutionException> {
            producer.sendBody("direct:first", false)
        }
    }
}