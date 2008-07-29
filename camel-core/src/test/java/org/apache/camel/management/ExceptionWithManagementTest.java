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
package org.apache.camel.management;

import junit.framework.AssertionFailedError;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;


/**
 * A testcase for exception handler when management is enabled (by default).
 * 
 * @version $Revision$
 */
public class ExceptionWithManagementTest extends ContextTestSupport {
    
    
    public void testExceptionHandler() throws Exception {
        MockEndpoint error = this.resolveMandatoryEndpoint("mock:error", MockEndpoint.class);
        error.expectedMessageCount(1);
        
        MockEndpoint out = this.resolveMandatoryEndpoint("mock:out", MockEndpoint.class);
        out.expectedMessageCount(0);
        
        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {    
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("hello");
            }

        });
        
        Thread.sleep(2000);
        error.assertIsSatisfied();
        out.assertIsSatisfied();
        
    }
    
    

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                exception(AssertionFailedError.class).maximumRedeliveries(1).to("mock:error");

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new AssertionFailedError("intentional error");
                    }
                }).to("mock:out");
            }
        };
    }
}
