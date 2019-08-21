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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class OnExceptionWhenSimpleOgnlTest extends ContextTestSupport {

    @Test
    public void testOnExceptionWhenSimpleOgnl() throws Exception {
        getMockEndpoint("mock:three").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(MyException.class)
                    // OGNL on the exception function in the simple language
                    .onWhen(simple("${exception.info.state} == 3")).handled(true).to("mock:three");

                from("direct:start").throwException(new MyException(3));
            }
        };
    }

    public static final class MyException extends Exception {
        private static final long serialVersionUID = 1L;
        private final MyExceptionInfo info;

        public MyException(int state) {
            this.info = new MyExceptionInfo(state);
        }

        public MyExceptionInfo getInfo() {
            return info;
        }
    }

    public static final class MyExceptionInfo {
        private final int state;

        public MyExceptionInfo(int state) {
            this.state = state;
        }

        public int getState() {
            return state;
        }
    }
}
