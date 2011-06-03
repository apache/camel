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
package org.apache.camel.component.ahc;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class AhcProduceTransferExceptionTest extends CamelTestSupport {

    @Test
    public void testAhcProduce() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", null);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            MyOrderException cause = assertIsInstanceOf(MyOrderException.class, e.getCause());
            assertNotNull(cause);
            assertEquals("123", cause.getOrderId());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("ahc:http://localhost:9080/foo?transferException=true")
                    .to("mock:result");

                from("jetty:http://localhost:9080/foo?transferException=true")
                    .throwException(new MyOrderException("123"));
            }
        };
    }
}
