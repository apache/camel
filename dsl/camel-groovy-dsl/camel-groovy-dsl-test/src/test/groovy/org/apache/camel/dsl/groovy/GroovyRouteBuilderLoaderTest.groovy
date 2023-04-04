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
package org.apache.camel.dsl.groovy

import org.apache.camel.Expression
import org.apache.camel.Predicate
import org.apache.camel.Processor
import org.apache.camel.component.jackson.JacksonDataFormat
import org.apache.camel.component.seda.SedaComponent
import org.apache.camel.dsl.groovy.support.MyBean
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.engine.DefaultChannel
import org.apache.camel.language.bean.BeanLanguage
import org.apache.camel.model.FromDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.rest.GetDefinition
import org.apache.camel.model.rest.PostDefinition
import org.apache.camel.processor.FatalFallbackErrorHandler
import org.apache.camel.processor.SendProcessor
import org.apache.camel.spi.HeaderFilterStrategy
import org.apache.camel.support.DefaultHeaderFilterStrategy
import org.apache.camel.support.PluginHelper
import spock.lang.AutoCleanup
import spock.lang.Specification

class GroovyRouteBuilderLoaderTest extends Specification {
    @AutoCleanup
    def context = new DefaultCamelContext()

    def loadRoute(String location) {
        def route = PluginHelper.getResourceLoader(context).resolveResource(location)
        PluginHelper.getRoutesLoader(context).loadRoutes(route)
    }

    def "load routes"() {
        when:
            loadRoute('/routes/routes.groovy')
        then:
            with(context.routeDefinitions) {
                it.size() == 1

                it[0].outputs[0] instanceof ToDefinition
                it[0].input.endpointUri == 'timer:tick'
            }
    }

    def "load routes with endpoint dsl"() {
        when:
            loadRoute('/routes/routes-with-endpoint-dsl.groovy')
        then:
            with(context.routeDefinitions) {
                it.size() == 1

                with(it[0].input, FromDefinition) {
                    it.endpointUri == 'timer://tick?period=1s'
                }
                with(it[0].outputs[0], ToDefinition) {
                    it.endpointUri == 'log://info'
                }
            }
    }

    def "load integration with rest"()  {
        when:
            loadRoute('/routes/routes-with-rest.groovy')
        then:
            context.restConfiguration.host == 'my-host'
            context.restConfiguration.port == 9192
            context.restDefinitions.size() == 2

            with(context.restDefinitions.find {it.path == '/my/path'}) {
                verbs.size() == 1

                with(verbs.first(), GetDefinition) {
                    path == '/get'
                    consumes == 'application/json'
                    produces == 'application/json'

                    with(to) {
                        endpointUri == 'direct:get'
                    }
                }
            }

            with(context.restDefinitions.find {it.path == '/post'}) {
                verbs.size() == 1

                with(verbs.first(), PostDefinition) {
                    path == null
                    consumes == 'application/json'
                    produces == 'application/json'

                    with(to) {
                        endpointUri == 'direct:post'
                    }
                }
            }
    }

    def "load integration with beans"()  {
        when:
            loadRoute('/routes/routes-with-beans.groovy')

        then:
            with(context.registry) {
                it.findByType(MyBean).size() == 1
                it.lookupByName('myBean') instanceof MyBean
                it.findByType(HeaderFilterStrategy).size() == 1
                it.lookupByName('filterStrategy') instanceof DefaultHeaderFilterStrategy
                it.lookupByName('myProcessor') instanceof Processor
                it.lookupByName('myPredicate') instanceof Predicate
                it.lookupByName('myExpression') instanceof Expression
            }
    }

    def "load integration with components configuration"()  {
        when:
            loadRoute('/routes/routes-with-components-configuration.groovy')

        then:
            with(context.getComponent('seda', SedaComponent)) {
                queueSize == 1234
                concurrentConsumers == 12
            }
            with(context.getComponent('mySeda', SedaComponent)) {
                queueSize == 4321
                concurrentConsumers == 21
            }
    }

    def "load integration with languages configuration"()  {
        when:
            loadRoute('/routes/routes-with-languages-configuration.groovy')

        then:
            with(context.resolveLanguage('bean'), BeanLanguage) {
                beanType == String.class
                method == "toUpperCase"
            }
            with(context.resolveLanguage('myBean'), BeanLanguage) {
                beanType == String.class
                method == "toLowerCase"
            }
    }

    def "load integration with dataformats configuration"()  {
        when:
            loadRoute('/routes/routes-with-dataformats-configuration.groovy')

        then:
            with(context.resolveDataFormat('jackson'), JacksonDataFormat) {
                unmarshalType == Map.class
                prettyPrint
            }
            with(context.resolveDataFormat('my-jackson'), JacksonDataFormat) {
                unmarshalType == String.class
                (!prettyPrint)
            }
    }

    def "load integration with component error property configuration"()  {
        when:
            loadRoute('/routes/routes-with-component-wrong-property-configuration.groovy')
        then:
            def e =  thrown MissingPropertyException
            assert e.message.contains("No such property: queueNumber for class: org.apache.camel.component.seda.SedaComponent")

    }

    def "load integration with component error method configuration"()  {
        when:
            loadRoute('/routes/routes-with-component-wrong-method-configuration.groovy')
        then:
            def e = thrown MissingMethodException
            assert e.message.contains("No signature of method: org.apache.camel.component.seda.SedaComponent.queueNumber()")

    }

    def "load integration with error handler"()  {
        when:
            loadRoute('/routes/routes-with-error-handler.groovy')
            context.start()
        then:
            context.routes?.size() == 1
            context.routes[0].getOnException('my-on-exception') != null
            context.routes[0].getOnException('my-on-exception') instanceof FatalFallbackErrorHandler

            def eh = context.routes[0].getOnException('my-on-exception')  as FatalFallbackErrorHandler
            def ch = eh.processor as DefaultChannel

            ch.output instanceof SendProcessor
    }

    // Test groovy eip extension, relates to https://issues.apache.org/jira/browse/CAMEL-14300
    def "load integration with eip"()  {
        when:
            loadRoute('/routes/routes-with-eip.groovy')
            context.start()
        then:
            1 == 1
    }

    def "load integration with set-header-variable"()  {
        when:
            loadRoute('/routes/routes-with-set-header-variable.groovy')
            context.start()
        then:
            1 == 1
    }
}
