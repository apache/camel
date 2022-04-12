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

import java.io.*;

import com.networknt.schema.JsonSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Applications that extended {@link DefaultJsonSchemaLoader} should continue to work.
 */
public class LegacyDefaultSchemaLoaderTest extends CamelTestSupport {

    @EndpointInject("mock:end")
    protected MockEndpoint endpoint;

    @Test
    public void testValidMessage() throws Exception {
        endpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "{ \"name\": \"Joe Doe\", \"id\": 1, \"price\": 12.5 }");

        MockEndpoint.assertIsSatisfied(endpoint);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        JsonValidatorEndpoint endpoint = context.getEndpoint(
                "json-validator:org/apache/camel/component/jsonvalidator/Order.json", JsonValidatorEndpoint.class);
        endpoint.setSchemaLoader(new DefaultJsonSchemaLoader() {
            @Override
            public JsonSchema createSchema(CamelContext camelContext, InputStream inputStream) throws Exception {
                // ignore the requested schema and always return schema.json schema ... the validation will only
                // succeed if it's done with this schema
                inputStream = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext,
                        "org/apache/camel/component/jsonvalidator/schema.json");
                return super.createSchema(camelContext, inputStream);
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("json-validator:org/apache/camel/component/jsonvalidator/Order.json")
                        .to("mock:end");
            }
        };
    }
}
