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
import org.apache.camel.model.Model
import org.apache.camel.model.validator.EndpointValidatorDefinition
import org.apache.camel.model.validator.CustomValidatorDefinition
import org.apache.camel.model.validator.PredicateValidatorDefinition

class ValidatorsTest extends YamlTestSupport {

    def "validators with endpointValidator"() {
        when:
        loadRoutes """
                - validators:
                    endpointValidator:
                      type: xml:XmlXOrderResponse
                      uri: "myxml:endpoint"
            """

        then:
        def validators = context.getCamelContextExtension().getContextPlugin(Model.class).getValidators()
        validators.size() == 1
        with(validators[0], EndpointValidatorDefinition) {
            it.type == 'xml:XmlXOrderResponse'
            it.uri == 'myxml:endpoint'
        }
    }

    def "validators with customValidator"() {
        when:
        loadRoutes """
                - validators:
                    customValidator:
                      type: other:OtherXOrder
                      className: org.example.OtherXOrderValidator
            """

        then:
        def validators = context.getCamelContextExtension().getContextPlugin(Model.class).getValidators()
        validators.size() == 1
        with(validators[0], CustomValidatorDefinition) {
            it.type == 'other:OtherXOrder'
            it.className == 'org.example.OtherXOrderValidator'
        }
    }

    def "validators with predicateValidator"() {
        when:
        loadRoutes """
                - validators:
                    predicateValidator:
                      type: xml:XmlXOrderResponse
                      expression:
                        simple: "\${body} != null"
            """

        then:
        def validators = context.getCamelContextExtension().getContextPlugin(Model.class).getValidators()
        validators.size() == 1
        with(validators[0], PredicateValidatorDefinition) {
            it.type == 'xml:XmlXOrderResponse'
            it.expression != null
        }
    }

    def "multiple validators"() {
        when:
        loadRoutes """
                - validators:
                    endpointValidator:
                      type: xml:XmlXOrderResponse
                      uri: "myxml:endpoint"
                    customValidator:
                      type: other:OtherXOrder
                      className: org.example.OtherXOrderValidator
            """

        then:
        def validators = context.getCamelContextExtension().getContextPlugin(Model.class).getValidators()
        validators.size() == 2
        validators[0] instanceof EndpointValidatorDefinition
        validators[1] instanceof CustomValidatorDefinition
    }
}
