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

import java.io.ByteArrayInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ValidatorInputStreamTest extends CamelTestSupport {
    
    @Test
    public void testReadTwice() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        String body = "{ \"name\": \"Joe Doe\", \"id\": 1, \"price\": 12.5 }";
        ByteArrayInputStream bais = new ByteArrayInputStream(body.getBytes());

        template.sendBody("direct:start", bais);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("json-validator:org/apache/camel/component/jsonvalidator/schema.json")
                    .to("mock:foo")
                    .to("json-validator:org/apache/camel/component/jsonvalidator/schema.json")
                    .to("mock:bar");
            }
        };
    }
}
