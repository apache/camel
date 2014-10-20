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
package org.apache.camel.component.validator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 *
 */
public class ValidatorIncludeEncodingRouteTest extends ContextTestSupport {

    protected MockEndpoint validEndpoint;
    protected MockEndpoint finallyEndpoint;
    protected MockEndpoint invalidEndpoint;

    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);
        
        String body = "<t:text xmlns:t=\"org.text\">\n"
                + "  <t:sentence>J'aime les cam\u00E9lid\u00E9s</t:sentence>\n"
                + "</t:text>";

        template.sendBody("direct:start", body);

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);
        finallyEndpoint = resolveMandatoryEndpoint("mock:finally", MockEndpoint.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .to("validator:org/apache/camel/component/validator/text.xsd")
                        .to("mock:valid")
                    .doCatch(NumberFormatException.class)
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            System.err.println("helo " + exchange.getException());                        
                        }
                    })
                        .to("mock:invalid")
                    .doFinally()
                        .to("mock:finally")
                    .end();
            }
        };
    }

}
