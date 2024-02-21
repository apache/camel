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

import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.kotlin.components.mock
import org.apache.camel.kotlin.components.`netty-http`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeansTest {

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
    fun testBeanRuntimeInstantiation() {
        camel(ctx) {
            bean("map", mutableMapOf(Pair("key", "value")))
            bean {
                name("test")
                type("org.apache.camel.kotlin.Example")
                property("map", "#map")
            }
        }

        val example = ctx.registry.lookupByName("test")
        assertTrue(example is Example)
        assertEquals("value", example.map["key"])

    }

    @Test
    fun testBeanBinding() {
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
        }
    }
}

class Example {
    lateinit var map: MutableMap<String, String>
}