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

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class ValidatorDeserializationFeaturesTest extends CamelTestSupport {

    @EndpointInject("mock:valid")
    protected MockEndpoint validEndpoint;

    @EndpointInject("mock:finally")
    protected MockEndpoint finallyEndpoint;

    @EndpointInject("mock:invalid")
    protected MockEndpoint invalidEndpoint;

    @Test
    public void testInvalidMessage() throws Exception {
        validEndpoint.expectedMessageCount(0);
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "{ \"name\": \"Joe Doe\", \"id\": 1, \"price\": 12.5 }secret");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .doTry()
                        .to("json-validator:org/apache/camel/component/jsonvalidator/schema.json" +
                                "?enabledDeserializationFeatures=FAIL_ON_TRAILING_TOKENS"
                        )
                        .to("mock:valid")
                        .doCatch(ValidationException.class)
                        .to("mock:invalid")
                        .doFinally()
                        .to("mock:finally")
                        .end();
            }
        };
    }
}
