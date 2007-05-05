/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision: 1.1 $
 */
public class RecipientListTest extends ContextTestSupport {
    protected MockEndpoint x, y, z;

    public void testSendingAMessageUsingMulticastReceivesItsOwnExchange() throws Exception {
        x.expectedBodiesReceived("answer");
        y.expectedBodiesReceived("answer");
        z.expectedBodiesReceived("answer");

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("answer");
                in.setHeader("recipientListHeader", "mock:x,mock:y,mock:z");
            }
        });

        MockEndpoint.assertIsSatisfied(x, y, z);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        x = (MockEndpoint) resolveMandatoryEndpoint("mock:x");
        y = (MockEndpoint) resolveMandatoryEndpoint("mock:y");
        z = (MockEndpoint) resolveMandatoryEndpoint("mock:z");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:a").recipientList(header("recipientListHeader").tokenize(","));
                // END SNIPPET: example
            }
        };

    }

}
