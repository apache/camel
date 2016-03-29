/**
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
package org.apache.camel.component.mock;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class MockPredicateEqualityTest extends ContextTestSupport {

    public void testByteArray() throws Exception {
        doTest(new byte[]{(byte) 0xde, (byte) 0xed, (byte) 0xbe, (byte) 0xef});
    }

    public void testIntArray() throws Exception {
        doTest(new int[]{121, 122, 123});
    }

    public void testCharArray() throws Exception {
        doTest("forbar".toCharArray());
    }
    public void testStringArray() throws Exception {
        doTest(new String[]{"this", "is", "an", "array"});
    }

    public void doTest(final Object anArray) throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.message(0).body().isEqualTo(anArray);
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", anArray);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").marshal().serialization().unmarshal().serialization().to("mock:reverse");
            }
        };
    }
}
