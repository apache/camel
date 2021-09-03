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
import org.apache.camel.dsl.yaml.support.model.MyBean

class BeansTest extends YamlTestSupport {

    def "beans"() {
        when:
            loadRoutes """
                - beans:
                  - name: myNested
                    type: ${MyBean.class.name}
                    properties:
                      field1: 'f1'
                      field2: 'f2'
                      nested:
                        foo: 'nf1'
                        bar: 'nf2'
                  - name: myProps
                    type: ${MyBean.class.name}
                    properties:
                      field1: 'f1_p'
                      field2: 'f2_p'
                      nested.foo: 'nf1_p'
                      nested.bar: 'nf2_p'
            """

        then:
            with(context.registry.lookupByName('myNested'), MyBean) {
                it.field1 == 'f1'
                it.field2 == 'f2'
                it.nested.foo == 'nf1'
                it.nested.bar == 'nf2'
            }
            with(context.registry.lookupByName('myProps'), MyBean) {
                it.field1 == 'f1_p'
                it.field2 == 'f2_p'
                it.nested.foo == 'nf1_p'
                it.nested.bar == 'nf2_p'
            }
    }

    def "beans with placeholders"() {
        when:
            context.propertiesComponent.setInitialProperties([
                'p1': 'f1',
                'p2': 'f2',
                'p3': 'f3',
            ] as Properties)

            loadRoutes """
                - beans:
                  - name: myNested
                    type: ${MyBean.class.name}
                    properties:
                      field1: '{{p1}}'
                      nested:
                        foo: '{{p2}}'
                  - name: myProps
                    type: ${MyBean.class.name}
                    properties:
                      nested.foo: '{{p3}}'
            """
        then:
            with(context.registry.lookupByName('myNested'), MyBean) {
                it.field1 == 'f1'
                it.nested.foo == 'f2'
            }
            with(context.registry.lookupByName('myProps'), MyBean) {
                it.nested.foo == 'f3'
            }
    }
}
