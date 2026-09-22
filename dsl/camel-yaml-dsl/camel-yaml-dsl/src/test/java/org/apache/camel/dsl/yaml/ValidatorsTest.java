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
package org.apache.camel.dsl.yaml;

import java.util.List;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.Model;
import org.apache.camel.model.validator.CustomValidatorDefinition;
import org.apache.camel.model.validator.EndpointValidatorDefinition;
import org.apache.camel.model.validator.PredicateValidatorDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorsTest extends YamlTestSupport {

    @Test
    void validatorsWithEndpointValidator() throws Exception {
        loadRoutes("""
                - validators:
                    endpointValidator:
                      type: xml:XmlXOrderResponse
                      uri: "myxml:endpoint"
                """);

        List<ValidatorDefinition> validators
                = context.getCamelContextExtension().getContextPlugin(Model.class).getValidators();
        assertThat(validators.size()).isEqualTo(1);

        assertThat(validators.get(0)).isInstanceOf(EndpointValidatorDefinition.class);
        EndpointValidatorDefinition ev = (EndpointValidatorDefinition) validators.get(0);
        assertThat(ev.getType()).isEqualTo("xml:XmlXOrderResponse");
        assertThat(ev.getUri()).isEqualTo("myxml:endpoint");
    }

    @Test
    void validatorsWithCustomValidator() throws Exception {
        loadRoutes("""
                - validators:
                    customValidator:
                      type: other:OtherXOrder
                      className: org.example.OtherXOrderValidator
                """);

        List<ValidatorDefinition> validators
                = context.getCamelContextExtension().getContextPlugin(Model.class).getValidators();
        assertThat(validators.size()).isEqualTo(1);

        assertThat(validators.get(0)).isInstanceOf(CustomValidatorDefinition.class);
        CustomValidatorDefinition cv = (CustomValidatorDefinition) validators.get(0);
        assertThat(cv.getType()).isEqualTo("other:OtherXOrder");
        assertThat(cv.getClassName()).isEqualTo("org.example.OtherXOrderValidator");
    }

    @Test
    void validatorsWithPredicateValidator() throws Exception {
        loadRoutes("""
                - validators:
                    predicateValidator:
                      type: xml:XmlXOrderResponse
                      expression:
                        simple: "${body} != null"
                """);

        List<ValidatorDefinition> validators
                = context.getCamelContextExtension().getContextPlugin(Model.class).getValidators();
        assertThat(validators.size()).isEqualTo(1);

        assertThat(validators.get(0)).isInstanceOf(PredicateValidatorDefinition.class);
        PredicateValidatorDefinition pv = (PredicateValidatorDefinition) validators.get(0);
        assertThat(pv.getType()).isEqualTo("xml:XmlXOrderResponse");
        assertThat(pv.getExpression()).isNotNull();
    }

    @Test
    void multipleValidators() throws Exception {
        loadRoutes("""
                - validators:
                    endpointValidator:
                      type: xml:XmlXOrderResponse
                      uri: "myxml:endpoint"
                    customValidator:
                      type: other:OtherXOrder
                      className: org.example.OtherXOrderValidator
                """);

        List<ValidatorDefinition> validators
                = context.getCamelContextExtension().getContextPlugin(Model.class).getValidators();
        assertThat(validators.size()).isEqualTo(2);
        assertThat(validators.get(0)).isInstanceOf(EndpointValidatorDefinition.class);
        assertThat(validators.get(1)).isInstanceOf(CustomValidatorDefinition.class);
    }
}
