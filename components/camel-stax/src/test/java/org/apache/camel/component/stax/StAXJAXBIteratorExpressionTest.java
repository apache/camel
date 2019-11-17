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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.stax.model.Record;
import org.apache.camel.component.stax.model.RecordsUtil;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.camel.component.stax.StAXBuilder.stax;

public class StAXJAXBIteratorExpressionTest extends CamelTestSupport {
    @EndpointInject("mock:records")
    private MockEndpoint recordsEndpoint;

    @BeforeClass
    public static void initRouteExample() {
        RecordsUtil.createXMLFile();
    }

    @Override
    public RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("file:target/in")
                    // split the file using StAX (the stax method is from StAXBuilder)
                    // and use streaming mode in the splitter
                    .split(stax(Record.class)).streaming()
                        .to("mock:records");
                // END SNIPPET: e1
            }
        };
    }

    @Test
    public void testStaxExpression() throws InterruptedException {
        recordsEndpoint.expectedMessageCount(10);
        recordsEndpoint.allMessages().body().isInstanceOf(Record.class);

        recordsEndpoint.assertIsSatisfied();

        Record five = recordsEndpoint.getReceivedExchanges().get(4).getIn().getBody(Record.class);
        assertEquals("4", five.getKey());
        assertEquals("#4", five.getValue());
    }
}
