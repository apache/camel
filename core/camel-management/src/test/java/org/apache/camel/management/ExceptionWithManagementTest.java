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
package org.apache.camel.management;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * A testcase for exception handler when management is enabled (by default).
 */
public class ExceptionWithManagementTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testExceptionHandler() throws Exception {
        MockEndpoint error = this.resolveMandatoryEndpoint("mock:error", MockEndpoint.class);
        error.expectedMessageCount(1);
        
        MockEndpoint out = this.resolveMandatoryEndpoint("mock:out", MockEndpoint.class);
        out.expectedMessageCount(0);
        
        template.send("direct:start", ExchangePattern.InOnly, exchange -> exchange.getIn().setBody("hello"));
        
        error.assertIsSatisfied();
        out.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                onException(IllegalArgumentException.class).redeliveryDelay(0).maximumRedeliveries(1).to("mock:error");

                from("direct:start").process(exchange -> {
                    throw new IllegalArgumentException("intentional error");
                }).to("mock:out");
            }
        };
    }
}
