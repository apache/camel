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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class NormalizerTest extends ContextTestSupport {

    @Test
    public void testSendToFirstWhen() throws Exception {
        String employeeBody1 = "<employee><name>Jon</name></employee>";
        String employeeBody2 = "<employee><name>Hadrian</name></employee>";
        String employeeBody3 = "<employee><name>Claus</name></employee>";
        String customerBody = "<customer name=\"James\"/>";

        MockEndpoint result = getMockEndpoint("mock:result");

        result.expectedMessageCount(4);
        result.expectedBodiesReceivedInAnyOrder("<person name=\"Jon\"/>", "<person name=\"Hadrian\"/>", "<person name=\"Claus\"/>", "<person name=\"James\"/>");

        template.sendBody("direct:start", employeeBody1);
        template.sendBody("direct:start", employeeBody2);
        template.sendBody("direct:start", employeeBody3);
        template.sendBody("direct:start", customerBody);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("normalizer", new MyNormalizer());
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                // we need to normalize two types of incoming messages
                from("direct:start").choice().when().xpath("/employee").to("bean:normalizer?method=employeeToPerson").when().xpath("/customer")
                    .to("bean:normalizer?method=customerToPerson").end().to("mock:result");
                // END SNIPPET: example
            }
        };
    }
}
