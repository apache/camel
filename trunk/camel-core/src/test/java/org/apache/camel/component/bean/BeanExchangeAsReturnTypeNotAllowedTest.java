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
package org.apache.camel.component.bean;

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.jndi.JndiContext;

/**
 * Unit test to demonstrate that bean invocation must no return Exchange.
 */
public class BeanExchangeAsReturnTypeNotAllowedTest extends ContextTestSupport {

    public void testExchangeAsReturnTypeNotAllowed() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        try {
            template.sendBody("direct:in", "Hello World");
            fail("Should have thrown IllegalStateException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            // expected
        }

        result.assertIsSatisfied();
    }

    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", new MyBean());
        return answer;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in")
                    .to("bean:myBean")
                    .to("mock:result");
            }
        };
    }

    public static class MyBean {
        public Exchange doSomething(Exchange exchange) {
            // this method should not be called
            return exchange;
        }
    }

}
