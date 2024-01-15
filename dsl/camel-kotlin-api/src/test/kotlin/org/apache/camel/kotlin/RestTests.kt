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
import reactor.netty.http.client.HttpClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

class RestTests {

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
    fun testRest() {
        val httpClient = HttpClient.create()

        val caught = AtomicBoolean(false)

        camel(ctx) {
            restConfiguration {
                component("netty-http")
                host("0.0.0.0")
                port(8080)
            }
            rest {
                get {
                    to { direct { name("get") } }
                }
            }
            route {
                from { direct { name("get") } }
                steps {
                    process { caught.set(true) }
                }
            }
        }
        ctx.start()

        httpClient.get().uri("localhost:8080").response().block()

        assertTrue(caught.get())
    }
}