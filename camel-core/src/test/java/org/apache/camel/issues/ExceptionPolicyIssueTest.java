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
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ExceptionPolicyIssueTest extends ContextTestSupport {

    public void testOnExceptionWithGenericException() throws Exception {
        getMockEndpoint("mock:exception").expectedMessageCount(0);
        getMockEndpoint("mock:ue").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(MyUnmarshalException.class).handled(true).to("mock:ue");
                
                onException(Exception.class).handled(true).to("mock:exception");

                from("direct:start")
                    .throwException(new MyUnmarshalException("Could not unmarshal", new IllegalArgumentException("Damn")));
            }
        };
    }

    private static final class MyUnmarshalException extends Exception {

        private static final long serialVersionUID = 1L;

        private MyUnmarshalException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
