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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision: 1.1 $
 */
public class ExceptionTest extends ContextTestSupport {

    public void testExceptionWithoutHandler() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(0);

        template.sendBody("direct:start", "<body/>");

        assertMockEndpointsSatisifed();
    }

    public void testExceptionWithHandler() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");

        exceptionEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedMessageCount(0);

        template.sendBody("direct:start", "<body/>");

        assertMockEndpointsSatisifed();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final Processor exceptionThrower = new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("<exception/>");
                throw new IllegalArgumentException("Exception thrown intentionally.");
            }
        };

        return new RouteBuilder() {
            public void configure() {
                if (getName().endsWith("WithHandler")) {
                    log.debug("Using exception handler");
                    exception(IllegalArgumentException.class).to("mock:exception");
                }
                from("direct:start").process(exceptionThrower).to("mock:result");
            }
        };
    }
}

