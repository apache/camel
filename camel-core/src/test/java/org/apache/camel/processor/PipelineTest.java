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
public class PipelineTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;

    public void testSendMessageThroughAPipeline() throws Exception {
        resultEndpoint.expectedBodiesReceived(4);

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(1);
                in.setHeader("foo", "bar");
            }
        });

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        resultEndpoint = (MockEndpoint) resolveMandatoryEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        final Processor processor = new Processor() {
            public void process(Exchange exchange) {
                Integer number = exchange.getIn().getBody(Integer.class);
                if (number == null) {
                    number = 0;
                }
                // todo set the endpoint name we were received from
                //exchange.setProperty(exchange.get);
                number = number + 1;
                exchange.getOut().setBody(number);
            }
        };

        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:a").pipeline("direct:x", "direct:y", "direct:z", "mock:result");
                // END SNIPPET: example

                from("direct:x").process(processor);
                from("direct:y").process(processor);
                from("direct:z").process(processor);
            }
        };
    }

}
