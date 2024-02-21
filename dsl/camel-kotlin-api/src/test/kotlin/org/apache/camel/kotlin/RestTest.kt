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
import org.apache.camel.kotlin.components.direct
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import reactor.netty.http.client.HttpClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RestTest {

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
    fun testRests() {
        val someReached = AtomicBoolean(false)

        camel(ctx) {
            restConfiguration {
                host("localhost")
                port("8080")
                component("netty-http")
                contextPath("/")
                apiContextPath("/openapi")
            }

            rest("/q") {
                get("/some") {
                    to {
                        direct { name("some") }
                    }
                }
            }

            route {
                from { direct { name("some") } }
                steps {
                    process { someReached.set(true) }
                }
            }
        }

        ctx.start()
        val client = HttpClient.create()
        assertDoesNotThrow {
            client.get().uri("http://localhost:8080/q/some").response().block()
        }
        assertTrue(someReached.get())
        val openapi = client.get().uri("http://localhost:8080/openapi").responseContent()
            .aggregate().asString().block()
        assertNotNull(openapi)
    }
}