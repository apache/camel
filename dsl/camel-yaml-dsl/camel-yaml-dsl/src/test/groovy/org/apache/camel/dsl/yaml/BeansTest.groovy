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
import org.apache.camel.dsl.yaml.support.model.MyBeanBuilder
import org.apache.camel.dsl.yaml.support.model.MyCtrBean
import org.apache.camel.dsl.yaml.support.model.MyDestroyBean
import org.apache.camel.dsl.yaml.support.model.MyFacBean
import org.apache.camel.dsl.yaml.support.model.MyFacHelper
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor

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

    def "beans with constructor"() {
        when:
        loadRoutes """
                - beans:
                  - name: myCtr
                    type: ${MyCtrBean.class.name}
                    constructors:
                      0: 'f1'
                      1: 'f2'
                    properties:
                      age: 42 
            """

        then:
        with(context.registry.lookupByName('myCtr'), MyCtrBean) {
            it.field1 == 'f1'
            it.field2 == 'f2'
            it.age == 42
        }
    }

    def "beans with constructor sorted"() {
        when:
        loadRoutes """
                - beans:
                  - name: myCtr
                    type: ${MyCtrBean.class.name}
                    constructors:
                      1: 'f2'
                      0: 'f1'
                    properties:
                      age: 43 
            """

        then:
        with(context.registry.lookupByName('myCtr'), MyCtrBean) {
            it.field1 == 'f1'
            it.field2 == 'f2'
            it.age == 43
        }
    }

    def "beans with factory"() {
        when:
        loadRoutes """
                - beans:
                  - name: myFac
                    type: ${MyFacBean.class.name}
                    factoryMethod: createBean
                    constructors:
                      0: 'fac1'
                      1: 'fac2'
                    properties:
                      age: 43 
            """

        then:
        with(context.registry.lookupByName('myFac'), MyFacBean) {
            it.field1 == 'fac1'
            it.field2 == 'fac2'
            it.age == 43
        }
    }

    def "beans with factory helper"() {
        when:
        loadRoutes """
                - beans:
                  - name: myFac
                    type: ${MyFacBean.class.name}
                    factoryBean: ${MyFacHelper.class.name}
                    factoryMethod: createBean
                    constructors:
                      0: 'fac1'
                      1: 'fac2'
                    properties:
                      age: 43 
            """

        then:
        with(context.registry.lookupByName('myFac'), MyFacBean) {
            it.field1 == 'fac1'
            it.field2 == 'fac2'
            it.age == 43
        }
    }

    def "beans with init destroy"() {
        when:
        loadRoutes """
                - beans:
                  - name: myBean
                    type: ${MyDestroyBean.class.name}
                    initMethod: initMe
                    destroyMethod: destroyMe
                    constructors:
                      0: 'fac1'
                      1: 'fac2'
                    properties:
                      age: 43 
            """

        then:

        MyDestroyBean.initCalled.get() == true
        MyDestroyBean.destroyCalled.get() == false

        with(context.registry.lookupByName('myBean'), MyDestroyBean) {
            it.field1 == 'fac1'
            it.field2 == 'fac2'
            it.age == 43
        }

        context.stop()

        MyDestroyBean.initCalled.get() == true
        MyDestroyBean.destroyCalled.get() == true
    }

    def "beans with script"() {
        when:
        loadRoutes """
                - beans:
                  - name: myBean
                    type: ${MyBean.class.name}
                    scriptLanguage: groovy
                    script: "var b = new ${MyBean.class.name}(); b.field1 = 'script1'; b.field2 = 'script2'; return b"
            """

        then:
        with(context.registry.lookupByName('myBean'), MyBean) {
            it.field1 == 'script1'
            it.field2 == 'script2'
        }
    }

    def "beans with builder class"() {
        when:
        loadRoutes """
                - beans:
                  - name: myBean
                    type: ${MyBean.class.name}
                    builderClass: ${MyBeanBuilder.class.name}
                    builderMethod: createTheBean
                    properties:
                      field1: builder1 
                      field2: builder2 
            """

        then:
        with(context.registry.lookupByName('myBean'), MyBean) {
            it.field1 == 'builder1'
            it.field2 == 'builder2'
        }
    }

}
