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
package org.apache.camel.component.jsonpatch;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonPatchComponentTest extends CamelTestSupport {

    @Test
    public void testCamelJsonPatch() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        sendBody("direct:foo", "{ \"a\": \"b\" }");
        assertEquals(1, mock.getReceivedExchanges().size());
        assertEquals("{\"c\":\"b\"}", mock.getReceivedExchanges().get(0).getIn().getBody(String.class));
    }

    @Test
    public void testSendToDeadLetterChannelIfPatchError() {
        MockEndpoint mock = getMockEndpoint("mock:errors");
        String source = "{ \"a\": \"b\" }";
        sendBody("direct:patch_error", source);
        assertEquals(1, mock.getReceivedExchanges().size());
        assertEquals(source, mock.getReceivedExchanges().get(0).getIn().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:foo")
                        .to("json-patch:org/apache/camel/component/jsonpatch/patch.json")
                        .to("mock:result");

                from("direct:patch_error")
                        .errorHandler(deadLetterChannel("mock:errors"))
                        .to("json-patch:org/apache/camel/component/jsonpatch/patch_error.json");

            }
        };
    }

}
