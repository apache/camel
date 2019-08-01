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
package org.apache.camel.component.stax;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.stax.model.RecordsUtil;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

public class StAXComponentRefTest extends CamelTestSupport {

    @EndpointInject("mock:records")
    private MockEndpoint recordsEndpoint;

    @BindToRegistry("myHandler")
    private CountingHandler handler = new CountingHandler();

    @BeforeClass
    public static void initRouteExample() {
        RecordsUtil.createXMLFile();
    }

    @Override
    public RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/in").routeId("stax-parser").to("stax:#myHandler").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        assertEquals(11, exchange.getIn().getBody(CountingHandler.class).getNumber());
                    }
                }).to("mock:records");
            }
        };
    }

    @Test
    public void testStax() throws Exception {
        recordsEndpoint.expectedMessageCount(1);
        recordsEndpoint.message(0).body().isInstanceOf(CountingHandler.class);

        recordsEndpoint.assertIsSatisfied();
    }
}
