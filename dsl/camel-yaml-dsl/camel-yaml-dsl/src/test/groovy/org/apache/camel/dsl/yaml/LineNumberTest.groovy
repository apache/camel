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

import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.model.ChoiceDefinition
import org.apache.camel.model.FromDefinition
import org.apache.camel.model.LogDefinition
import org.apache.camel.model.RouteDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.support.PluginHelper

class LineNumberTest extends YamlTestSupport {

    def "line number definition"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - log:
                         logging-level: "ERROR"
                         message: "test"
                         log-name: "yaml"
                      - to: "direct:result"
            '''
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0].input, FromDefinition) {
                uri == "direct:start"
                lineNumber == 2
            }
            with(context.routeDefinitions[0].outputs[0], LogDefinition) {
                loggingLevel == 'ERROR'
                message == 'test'
                logName == 'yaml'
                lineNumber == 5
            }
            with(context.routeDefinitions[0].outputs[1], ToDefinition) {
                uri == "direct:result"
                lineNumber == 9
            }
    }

    def "line number route"() {
        when:
        loadRoutes '''
                - route:
                    from:
                      uri: "direct:info"
                      steps:
                        - log: "message"
            '''
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0], RouteDefinition) {
            input.lineNumber == 4
            input.endpointUri == 'direct:info'

            with (outputs[0], LogDefinition) {
                lineNumber == 6
                message == 'message'
            }
        }
    }

    def "line number file"() {
        setup:
        def rloc = 'classpath:/stuff/my-route.yaml'
        def rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc)
        when:
        loadRoutes rdsl
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0].input, FromDefinition) {
            uri == "quartz:foo?cron={{myCron}}"
            lineNumber == 21
        }
        with(context.routeDefinitions[0].outputs[0], LogDefinition) {
            message == 'Start'
            lineNumber == 23
        }
        with(context.routeDefinitions[0].outputs[1], ToDefinition) {
            uri == "bean:myBean?method=hello"
            lineNumber == 24
        }
        with(context.routeDefinitions[0].outputs[3], ToDefinition) {
            uri == "bean:myBean?method=bye"
            lineNumber == 26
        }
        with(context.routeDefinitions[0].outputs[4], LogDefinition) {
            message == '${body}'
            lineNumber == 27
        }
        with(context.routeDefinitions[0].outputs[5], ChoiceDefinition) {
            lineNumber == 28
        }
        with(context.routeDefinitions[0].outputs[6], LogDefinition) {
            message == '${header.textProp}'
            lineNumber == 40
        }
    }

    def "line number file with comments"() {
        setup:
        def rloc = 'classpath:/stuff/my-route-comment.yaml'
        def rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc)
        when:
        loadRoutes rdsl
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0].input, FromDefinition) {
            uri == "quartz:foo?cron={{myCron}}"
            lineNumber == 23
        }
        with(context.routeDefinitions[0].outputs[0], LogDefinition) {
            message == 'Start'
            lineNumber == 26
        }
        with(context.routeDefinitions[0].outputs[1], ToDefinition) {
            uri == "bean:myBean?method=hello"
            lineNumber == 27
        }
        with(context.routeDefinitions[0].outputs[3], ToDefinition) {
            uri == "bean:myBean?method=bye"
            lineNumber == 31
        }
        with(context.routeDefinitions[0].outputs[4], LogDefinition) {
            message == '${body}'
            lineNumber == 32
        }
        with(context.routeDefinitions[0].outputs[5], ChoiceDefinition) {
            lineNumber == 34 // TODO: should be 32
        }
        with(context.routeDefinitions[0].outputs[6], LogDefinition) {
            message == '${header.textProp}'
            lineNumber == 50
        }
    }

}
