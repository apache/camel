/**
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
package org.apache.camel.dsl.kotlin

import org.apache.camel.Predicate
import org.apache.camel.Processor
import org.apache.camel.RuntimeCamelException
import org.apache.camel.component.jackson.JacksonDataFormat
import org.apache.camel.component.log.LogComponent
import org.apache.camel.component.seda.SedaComponent
import org.apache.camel.dsl.kotlin.support.MyBean
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.language.bean.BeanLanguage
import org.apache.camel.model.ProcessDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.rest.GetVerbDefinition
import org.apache.camel.model.rest.PostVerbDefinition
import org.apache.camel.processor.FatalFallbackErrorHandler
import org.apache.camel.support.DefaultHeaderFilterStrategy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

class KotlinRoutesBuilderLoaderTest {

    @Test
    fun `load routes`() {
        val ctx = DefaultCamelContext()
        val res = ctx.resourceLoader.resolveResource("/routes/routes.kts")

        ctx.routesLoader.loadRoutes(res)

        val routes = ctx.routeDefinitions

        assertThat(routes).hasSize(1)
        assertThat(routes[0].input.endpointUri).isEqualTo("timer:tick")
        assertThat(routes[0].outputs[0]).isInstanceOf(ProcessDefinition::class.java)
        assertThat(routes[0].outputs[1]).isInstanceOf(ToDefinition::class.java)
    }

    @Test
    fun `load routes with endpoint dsl`() {
        val ctx = DefaultCamelContext()
        val res = ctx.resourceLoader.resolveResource("/routes/routes-with-endpoint-dsl.kts")

        ctx.routesLoader.loadRoutes(res)

        val routes = ctx.routeDefinitions

        assertThat(routes).hasSize(1)
        assertThat(routes[0].input.endpointUri).isEqualTo("timer://tick?period=1s")
        assertThat(routes[0].outputs[0]).isInstanceOfSatisfying(ToDefinition::class.java) {
            assertThat(it.endpointUri).isEqualTo("log://info")
        }
    }


    @Test
    fun `load integration with rest`() {
        val ctx = DefaultCamelContext()
        val res = ctx.resourceLoader.resolveResource("/routes/routes-with-rest.kts")

        ctx.routesLoader.loadRoutes(res)

        assertThat(ctx.restConfiguration.host).isEqualTo("my-host")
        assertThat(ctx.restConfiguration.port).isEqualTo(9192)
        assertThat(ctx.restDefinitions.size).isEqualTo(2)

        with(ctx.restDefinitions.find { it.path == "/my/path" }) {
            assertThat(this?.verbs).hasSize(1)

            with(this?.verbs?.get(0) as GetVerbDefinition) {
                assertThat(uri).isEqualTo("/get")
                assertThat(consumes).isEqualTo("application/json")
                assertThat(produces).isEqualTo("application/json")
                assertThat(to).hasFieldOrPropertyWithValue("endpointUri", "direct:get")
            }
        }

        with(ctx.restDefinitions.find { it.path == "/post" }) {
            assertThat(this?.verbs).hasSize(1)

            with(this?.verbs?.get(0) as PostVerbDefinition) {
                assertThat(uri).isNull()
                assertThat(consumes).isEqualTo("application/json")
                assertThat(produces).isEqualTo("application/json")
                assertThat(to).hasFieldOrPropertyWithValue("endpointUri", "direct:post")
            }
        }
    }

    @Test
    fun `load integration with beans`() {
        val ctx = DefaultCamelContext()
        val res = ctx.resourceLoader.resolveResource("/routes/routes-with-beans.kts")

        ctx.routesLoader.loadRoutes(res)

        assertThat(ctx.registry.findByType(MyBean::class.java)).hasSize(1)
        assertThat(ctx.registry.lookupByName("myBean")).isInstanceOf(MyBean::class.java)
        assertThat(ctx.registry.findByType(DefaultHeaderFilterStrategy::class.java)).hasSize(1)
        assertThat(ctx.registry.lookupByName("filterStrategy")).isInstanceOf(DefaultHeaderFilterStrategy::class.java)
        assertThat(ctx.registry.lookupByName("myProcessor")).isInstanceOf(Processor::class.java)
        assertThat(ctx.registry.lookupByName("myPredicate")).isInstanceOf(Predicate::class.java)
    }

    @Test
    fun `load integration with components configuration`() {
        val ctx = DefaultCamelContext()
        val res = ctx.resourceLoader.resolveResource("/routes/routes-with-components-configuration.kts")

        ctx.routesLoader.loadRoutes(res)

        val seda = ctx.getComponent("seda", SedaComponent::class.java)
        val mySeda = ctx.getComponent("mySeda", SedaComponent::class.java)
        val log = ctx.getComponent("log", LogComponent::class.java)

        assertThat(seda.queueSize).isEqualTo(1234)
        assertThat(seda.concurrentConsumers).isEqualTo(12)
        assertThat(mySeda.queueSize).isEqualTo(4321)
        assertThat(mySeda.concurrentConsumers).isEqualTo(21)
        assertThat(log.exchangeFormatter).isNotNull
    }

    @Test
    fun `load integration with components configuration error`() {
        assertThatExceptionOfType(RuntimeCamelException::class.java)
                .isThrownBy {
                    val ctx = DefaultCamelContext()
                    val res = ctx.resourceLoader.resolveResource("/routes/routes-with-components-configuration-error.kts")

                    ctx.routesLoader.loadRoutes(res)
                }
                .withCauseInstanceOf(IllegalArgumentException::class.java)
                .withMessageContaining("Type mismatch, expected: class org.apache.camel.component.log.LogComponent, got: class org.apache.camel.component.seda.SedaComponent");
    }

    @Test
    fun `load integration with languages configuration`() {
        val ctx = DefaultCamelContext()
        val res = ctx.resourceLoader.resolveResource("/routes/routes-with-languages-configuration.kts")

        ctx.routesLoader.loadRoutes(res)

        val bean = ctx.resolveLanguage("bean") as BeanLanguage
        assertThat(bean.beanType).isEqualTo(String::class.java)
        assertThat(bean.method).isEqualTo("toUpperCase")

        val mybean = ctx.resolveLanguage("my-bean") as BeanLanguage
        assertThat(mybean.beanType).isEqualTo(String::class.java)
        assertThat(mybean.method).isEqualTo("toLowerCase")
    }

    @Test
    fun `load integration with dataformats configuration`() {
        val ctx = DefaultCamelContext()
        val res = ctx.resourceLoader.resolveResource("/routes/routes-with-dataformats-configuration.kts")

        ctx.routesLoader.loadRoutes(res)

        val jackson = ctx.resolveDataFormat("json-jackson") as JacksonDataFormat
        assertThat(jackson.unmarshalType).isEqualTo(Map::class.java)
        assertThat(jackson.isPrettyPrint).isTrue()

        val myjackson = ctx.resolveDataFormat("my-jackson") as JacksonDataFormat
        assertThat(myjackson.unmarshalType).isEqualTo(String::class.java)
        assertThat(myjackson.isPrettyPrint).isFalse()
    }

    @Test
    fun `load integration with error handler`() {
        val ctx = DefaultCamelContext()
        val res = ctx.resourceLoader.resolveResource("/routes/routes-with-error-handler.kts")

        ctx.routesLoader.loadRoutes(res)
        ctx.start()

        try {
            assertThat(ctx.routes).hasSize(1)
            assertThat(ctx.routes[0].getOnException("my-on-exception")).isInstanceOf(FatalFallbackErrorHandler::class.java)
        } finally {
            ctx.stop()
        }
    }
}
