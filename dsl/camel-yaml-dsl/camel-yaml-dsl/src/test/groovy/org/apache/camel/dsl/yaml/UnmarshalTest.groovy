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
import org.apache.camel.model.UnmarshalDefinition
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper
import org.apache.camel.support.ResourceHelper

class UnmarshalTest extends YamlTestSupport {

    def "unmarshal definition (#resource.location, #expected)"(Resource resource, String expected) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], UnmarshalDefinition) {
                with(dataFormatType) {
                    dataFormatName == expected
                }
            }
        where:
            resource << [
                asResource('data-format', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - unmarshal:
                             json: 
                               library: Gson
                          - to: "mock:result"
                    '''),
                asResource('data-format-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - unmarshal:
                             data-format-type:
                               json: 
                                 library: Gson
                          - to: "mock:result"
                    '''),
                asResource('data-format', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - unmarshal:
                             json: {}
                          - to: "mock:result"
                    '''),
                asResource('data-format-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - unmarshal:
                             data-format-type:
                               json: {}
                          - to: "mock:result"
                    '''),
                        asResource('data-format-xml', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - unmarshal:
                             jacksonXml: {}
                          - to: "mock:result"
                    '''),
            ]

            expected << [
                'gson', 'gson', 'jackson', 'jackson', 'jacksonXml'
            ]
    }

    def "unmarshal definition with allow null body (#resource.location, #expected)"(Resource resource, String expected) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], UnmarshalDefinition) {
                allowNullBody == expected
            }
        where:
            resource << [
                asResource('allow-null-body-set-to-true', '''
                    - from:
                        uri: "direct:start"
                        steps:
                          - unmarshal:
                             allow-null-body: true
                             json:
                               library: Gson
                          - to: "mock:result"
                    '''),
                asResource('allow-null-body-set-to-false', '''
                    - from:
                        uri: "direct:start"
                        steps:
                          - unmarshal:
                             allow-null-body: false
                             json:
                               library: Gson
                          - to: "mock:result"
                    '''),
                asResource('allow-null-body-not-set', '''
               - from:
                        uri: "direct:start"
                        steps:
                          - unmarshal:
                             json:
                               library: Gson
                          - to: "mock:result"
                    ''')
            ]
        expected << [
                'true', 'false', null
        ]
    }

    // CAMEL-24847: a data format named as its artifact or catalog entry says which key and option to write
    def "#eip with #key fails with a message naming the data format key"(String eip, String key, String hint) {
        when:
            loadRoutes([ResourceHelper.fromString("route-1.yaml", """
                - from:
                    uri: timer:tick
                    steps:
                      - ${eip}:
                          ${key}: {}
            """.stripIndent())], false)
        then:
            def e = thrown(Exception)
            def messages = []
            for (Throwable t = e; t != null; t = t.cause) {
                messages << t.message
            }
            messages.any { it != null && it.contains("Error constructing YAML node id: ${eip}: unsupported field: ${key}") && it.contains(hint) }
        where:
            eip         | key            | hint
            'unmarshal' | 'jackson'      | 'the data format is json, Jackson is its library: write json: {library: Jackson}'
            'unmarshal' | 'json-jackson' | 'write json: {library: Jackson}'
            'unmarshal' | 'gson'         | 'write json: {library: Gson}'
            'unmarshal' | 'bindy-csv'    | 'the data format is bindy, Csv is its type: write bindy: {type: Csv}'
            'unmarshal' | 'snake-yaml'   | 'the data format is yaml: write yaml: {...}'
            'unmarshal' | 'JSON'         | "did you mean 'json'?"
            'marshal'   | 'jackson'      | 'the data format is json, Jackson is its library: write json: {library: Jackson}'
            'marshal'   | 'JSON'         | "did you mean 'json'?"
    }
}
