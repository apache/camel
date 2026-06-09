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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class JsonValidatorCustomObjectMapperTest extends CamelTestSupport {

    @BindToRegistry("myCustomMapper")
    public ObjectMapper createCustomMapper() {
        return JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .build();
    }

    @Test
    public void testCustomObjectMapperIsUsed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:valid");
        mock.expectedMessageCount(1);

        String jsonWithSingleQuotes = "{'id': 101, 'name': 'Camel Validator', 'price': 99.99}";

        template.sendBody("direct:start", jsonWithSingleQuotes);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("json-validator:org/apache/camel/component/jsonvalidator/schema.json?objectMapper=#myCustomMapper")
                        .to("mock:valid");
            }
        };
    }
}
