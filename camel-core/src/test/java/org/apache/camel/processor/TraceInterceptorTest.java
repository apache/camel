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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.interceptor.Debugger;

/**
 * @version $Revision: 1.1 $
 */
public class TraceInterceptorTest extends ContextTestSupport {
    public void testSendingSomeMessages() throws Exception {
        template.sendBodyAndHeader("direct:start", "body1", "header1", "value1");
        template.sendBodyAndHeader("direct:start", "body2", "header1", "value2");
    }


    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // lets add the debugger which traces by default
                getContext().addInterceptStrategy(new Debugger());

                from("direct:start").
                        process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // do nothing
                            }

                            @Override
                            public String toString() {
                                return "MyProcessor";
                            }
                        }).
                        to("mock:a").
                        to("mock:b");
            }
        };
    }
}
