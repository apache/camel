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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class OnExceptionProcessIssueTest extends ContextTestSupport {

    @Test
    public void testOnExceptionProcessIssue() throws Exception {
        Exchange out = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });
        assertNotNull(out);
        assertEquals("bar", out.getIn().getHeader("foo"));
        assertEquals("ERROR: Damn for message: Hello World", out.getIn().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).useOriginalMessage().handled(true).setHeader("foo", constant("bar")).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                        in.setBody("ERROR: " + ex.getMessage() + " for message: " + in.getBody());
                    }
                });

                from("direct:start").transform(constant("Bye World")).throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
