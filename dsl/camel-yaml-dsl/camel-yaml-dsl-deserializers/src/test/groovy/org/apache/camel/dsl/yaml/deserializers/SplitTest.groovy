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
package org.apache.camel.dsl.yaml.deserializers

import org.apache.camel.dsl.yaml.common.YamlDeserializationContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.model.SplitDefinition
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import spock.lang.Specification

class SplitTest extends Specification {

    def "split"() {
        given:
            def settings = LoadSettings.builder().build()

            def ctr = new YamlDeserializationContext(settings)
            ctr.setCamelContext(new DefaultCamelContext())
            ctr.addResolver(new CustomResolver())
            ctr.addResolver(new ModelDeserializersResolver())
        when:
            def load = new Load(settings, ctr)

            def result = load.loadFromString('''
                - split:
                    simple: test
                    steps:
                      - to:
                          uri: to1                          
                      - to:
                          uri: to2
            '''.stripLeading())
        then:
            with(result, List) {
                size() == 1

                with(get(0), SplitDefinition) {
                    outputs.size() == 2
                    expression != null
                    expression.language == 'simple'
                    expression.expression == 'test'
                }
            }
    }

    def "split with expression block"() {
        given:
            def settings = LoadSettings.builder().build()

            def ctr = new YamlDeserializationContext(settings)
            ctr.setCamelContext(new DefaultCamelContext())
            ctr.addResolver(new CustomResolver())
            ctr.addResolver(new ModelDeserializersResolver())
        when:

            def load = new Load(settings, ctr)

            def result = load.loadFromString('''
                - split:
                    expression:
                      simple: test
                    steps:
                      - to:
                          uri: to1                          
                      - to:
                          uri: to2
            '''.stripLeading())
        then:
            with(result, List) {
                size() == 1

                with(get(0), SplitDefinition) {
                    outputs.size() == 2
                    expression != null
                    expression.language == 'simple'
                    expression.expression == 'test'
                }
            }

    }
}
