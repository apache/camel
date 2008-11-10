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
package org.apache.camel.processor;

import javax.naming.Context;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.XPath;
import org.apache.camel.util.jndi.JndiContext;
import static org.apache.camel.component.mock.MockEndpoint.expectsMessageCount;

public class NormalizerTest extends ContextTestSupport {
    protected MockEndpoint result;
    protected MyNormalizer myNormalizer = new MyNormalizer();
    
    public void testSendToFirstWhen() throws Exception {
        String employeeBody1 = "<employee><name>Jon</name></employee>";
        String employeeBody2 = "<employee><name>Hadrian</name></employee>";
        String employeeBody3 = "<employee><name>Claus</name></employee>";        
        String customerBody = "<customer name=\"James\"/>";
        
        // expect only one person named Jon
        result.expectedMessageCount(1);
        result.expectedBodiesReceived("<person name=\"Jon\"/>");

        template.sendBody("direct:start", employeeBody1);
        template.sendBody("direct:start", employeeBody2);
        template.sendBody("direct:start", employeeBody3);        
        template.sendBody("direct:start", customerBody);        
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        result = getMockEndpoint("mock:result");
    }
    
    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("normalizer", myNormalizer);
        return answer;
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example                
                // before we can filter, we need to normalize the incoming messages
                from("direct:start").choice()
                  .when().xpath("/employee").to("bean:normalizer?method=employeeToPerson").to("seda:queue")
                  .when().xpath("/customer").to("bean:normalizer?method=customerToPerson").to("seda:queue");
                
                // filter the normalized messages
                from("seda:queue").filter().xpath("/person[@name='Jon']").to("mock:result");
                // END SNIPPET: example
            }
        };
    }
}
