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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class Issue3Test extends ContextTestSupport {
    protected String fromQueue = "direct:A";

    public void testIssue() throws Exception {
        sendBody(fromQueue, "cluster!");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fromQueue).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        final Message in = exchange.getIn();
                        assertNotNull("Message is Null", in);
                        String isDebugString = in.getHeader("someproperty", String.class);
                        assertNull("Header should be null but is: " + isDebugString, isDebugString);
                        assertNotNull("Message is Null", in);

                        Boolean isDebug = in.getHeader("someproperty", Boolean.class);
                        assertNull(isDebug);

                        boolean isDebug2 = in.getHeader("someproperty", boolean.class);
                        assertFalse(isDebug2);
                    }
                });
            }
        };
    }
}
