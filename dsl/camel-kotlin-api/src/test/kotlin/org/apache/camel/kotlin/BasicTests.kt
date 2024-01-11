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
import org.apache.camel.kotlin.components.mock
import org.apache.camel.kotlin.components.`netty-http`
import org.apache.camel.kotlin.dataformats.csv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BasicTests {

    @Test
    fun testRouteId() {
        val ctx = DefaultCamelContext()
        camel(ctx) {
            route {
                id("first")
            }
        }
        assertEquals(1, ctx.routeDefinitions.size)
        val def = ctx.routeDefinitions[0]
        assertEquals("first", def.id)
    }

    @Test
    fun testBeanDefinition() {
        val ctx = DefaultCamelContext()
        camel(ctx) {
            route {
                from {
                    `netty-http` {
                        protocol("http")
                        host("localhost")
                        port(8080)
                        path("/")
                        bossGroup("#nioELG")
                    }
                }
                steps {
                    to {
                        mock {
                            name("end")
                        }
                    }
                }
            }
            bean {
                name("nioELG")
                type("io.netty.channel.nio.NioEventLoopGroup")
            }
        }
        assertDoesNotThrow {
            ctx.start()
            ctx.stop()
        }
    }

    @Test
    fun testRestDefinition() {
        val ctx = DefaultCamelContext()
        camel(ctx) {
            restConfiguration {
                host("localhost")
                port(8080)
                component("netty-http")
            }
            rest {
                get("/get") {
                    to {
                        direct { name("get") }
                    }
                }
            }
            route {
                from {
                    direct { name("get") }
                }
                steps {
                    to {
                        mock { name("get") }
                    }
                }
            }
        }
        assertDoesNotThrow {
            ctx.start()
            ctx.stop()
        }
    }

    @Test
    fun testTryCatch() {
        val ctx = DefaultCamelContext()
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
        ctx.stop()
    }

    @Test
    fun testUnmarshal() {
        val ctx = DefaultCamelContext()
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
        ctx.stop()
    }

    @Test
    fun testMulticast() {
        val ctx = DefaultCamelContext()
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
        ctx.stop()
    }

    @Test
    fun testValidate() {
        val ctx = DefaultCamelContext()
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
        ctx.stop()
    }
}