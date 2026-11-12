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
package org.apache.camel.component.jsonvalidator;

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.ValidationException;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ValidatorObjectMapperTest {

    private static final String SCHEMA = "json-validator:org/apache/camel/component/jsonvalidator/schema.json";
    private static final String TRAILING = "{ \"name\": \"Joe Doe\", \"id\": 1, \"price\": 12.5 }secret";

    @Test
    public void defaultMapperKeepsJackson2Defaults() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            // FAIL_ON_TRAILING_TOKENS is off in Jackson 2 and on in Jackson 3
            assertThat(validate(context, SCHEMA, TRAILING)).isNull();
        }
    }

    @Test
    public void registryMapperIsUsed() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("mapper", JsonMapper.builder().build());
            context.start();

            assertThat(validate(context, SCHEMA, TRAILING)).isInstanceOf(ValidationException.class);
        }
    }

    @Test
    public void featuresDoNotChangeTheConfiguredMapper() throws Exception {
        ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("mapper", mapper);
            context.start();

            JsonValidatorEndpoint endpoint = context.getEndpoint(
                    SCHEMA + "?enabledDeserializationFeatures=FAIL_ON_TRAILING_TOKENS", JsonValidatorEndpoint.class);
            endpoint.start();

            assertThat(validate(context, endpoint.getEndpointUri(), TRAILING)).isInstanceOf(ValidationException.class);
            assertThat(endpoint.getObjectMapper().isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).isTrue();
            assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).isFalse();
        }
    }

    @Test
    public void featureNamesAreTrimmed() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            JsonValidatorEndpoint endpoint = context.getEndpoint(
                    SCHEMA + "?enabledDeserializationFeatures=FAIL_ON_TRAILING_TOKENS, FAIL_ON_READING_DUP_TREE_KEY",
                    JsonValidatorEndpoint.class);
            endpoint.start();

            assertThat(endpoint.getObjectMapper().isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).isTrue();
            assertThat(endpoint.getObjectMapper().isEnabled(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)).isTrue();
        }
    }

    @Test
    public void unknownFeatureNameFails() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            assertThatThrownBy(() -> context.getEndpoint(SCHEMA + "?enabledDeserializationFeatures=READ_ENUMS_USING_TO_STRING"))
                    .isInstanceOf(ResolveEndpointFailedException.class)
                    .hasMessageContaining("Unknown deserialization feature: READ_ENUMS_USING_TO_STRING")
                    .hasMessageContaining(DeserializationFeature.class.getName());
        }
    }

    @Test
    public void mapperIdOfAnotherTypeFails() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("notAMapper", "some text");
            context.start();

            JsonValidatorComponent component = context.getComponent("json-validator", JsonValidatorComponent.class);
            component.setObjectMapper("notAMapper");

            assertThatThrownBy(() -> context.getEndpoint(SCHEMA))
                    .isInstanceOf(ResolveEndpointFailedException.class)
                    .hasRootCauseInstanceOf(NoSuchBeanException.class);
        }
    }

    private static Exception validate(CamelContext context, String uri, String body) {
        return context.createProducerTemplate()
                .request(uri, exchange -> exchange.getIn().setBody(body))
                .getException();
    }
}
