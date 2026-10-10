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
package org.apache.camel.dsl.yaml

import java.nio.file.Files

import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.DefaultDumpRoutesStrategy
import org.apache.camel.model.OnExceptionDefinition
import org.apache.camel.model.RoutesDefinition
import org.apache.camel.yaml.LwModelToYAMLDumper

class OnExceptionYamlDumpTest extends YamlTestSupport {

    def "top-level onException is dumped as top-level (started: #started)"() {
        setup:
            loadRoutes """
                - onException:
                    exception:
                      - java.lang.Exception
                    handled:
                      constant: "true"
                    steps:
                      - to: mock:error
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
                - route:
                    id: second
                    from:
                      uri: direct:second
                      steps:
                        - to: mock:second
            """
            if (started) {
                context.start()
            }
        when:
            def yaml = dumpRoutes()
            if (started) {
                // reload the dump in a new context, not in the started one (a stopped context has no type converter
                // to build the endpoint uris of the dump with)
                context.close()
                context = new DefaultCamelContext()
            }
        then:
            // written once, before the routes, and not in the steps of each route
            yaml.startsWith('- onException:')
            yaml.split('onException:', -1).length == 2
        when:
            // the dump is valid for the YAML DSL schema, and the onException still applies to all the routes
            context.removeRouteDefinitions(List.copyOf(context.routeDefinitions))
            loadRoutes yaml
        then:
            ['first', 'second'].each {
                def oe = context.getRouteDefinition(it).outputs[0]
                assert oe instanceof OnExceptionDefinition
                assert !oe.routeScoped
                assert oe.exceptions == ['java.lang.Exception']
            }
        where:
            started << [false, true]
    }

    def "routeConfiguration onException is not dumped in the routes"() {
        setup:
            loadRoutes """
                - routeConfiguration:
                    onException:
                      - onException:
                          exception:
                            - java.lang.Exception
                          handled:
                            constant: "true"
                          steps:
                            - to: mock:error
            """
            loadRoutes """
                - route:
                    id: myRoute
                    from:
                      uri: direct:start
                      steps:
                        - to: mock:result
            """
        expect:
            context.getRouteDefinition('myRoute').outputs[0] instanceof OnExceptionDefinition
        when:
            // the onException is dumped with the route configuration
            def yaml = dumpRoutes()
        then:
            !yaml.contains('onException')
            yaml.contains('id: myRoute')
        when:
            context.removeRouteDefinition(context.getRouteDefinition('myRoute'))
            loadRoutes yaml
        then:
            context.getRouteDefinition('myRoute') != null
    }

    def "onException of another file is dumped before the routes in one file"() {
        setup:
            // two files normalized into one, as camel validate normalize a.yaml b.yaml does: the onException of the
            // second file must come before the route of the first one, or the dump does not load
            loadRoutes """
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
            """, """
                - onException:
                    exception:
                      - java.lang.Exception
                    handled:
                      constant: "true"
                    steps:
                      - to: mock:error
                - route:
                    id: second
                    from:
                      uri: direct:second
                      steps:
                        - to: mock:second
            """
            def output = Files.createTempDirectory('normalize').resolve('normalized.yaml')
        when:
            def dump = new DefaultDumpRoutesStrategy()
            dump.camelContext = context
            dump.include = 'routes'
            dump.log = false
            dump.uriAsParameters = true
            dump.output = output.toString()
            dump.dumpRoutes('yaml')
            def yaml = Files.readString(output)
        then:
            yaml.startsWith('- onException:')
            yaml.split('onException:', -1).length == 2
            yaml.contains('id: first')
            yaml.contains('id: second')
        when:
            // the combined dump is valid for the YAML DSL schema and loads
            context.removeRouteDefinitions(List.copyOf(context.routeDefinitions))
            loadRoutes yaml
        then:
            context.routeDefinitions.size() == 2
        cleanup:
            output.toFile().delete()
            output.parent.toFile().delete()
    }

    def dumpRoutes() {
        // as the routes are dumped by camel.main.dumpRoutes, which camel validate normalize uses
        def routes = new RoutesDefinition()
        routes.routes.addAll(context.routeDefinitions)
        return new LwModelToYAMLDumper().dumpModelAsYaml(context, routes, false, true, false, false)
    }
}
