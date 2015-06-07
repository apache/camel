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
import org.apache.camel.ExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.jndi.JndiContext;

public class BeanWithExchangeExceptionAnnotationTest extends ContextTestSupport {

    public void testBeanWithAnnotationAndExchangeTest() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        MockEndpoint error = getMockEndpoint("mock:error");
        
        result.expectedMessageCount(0);
        error.expectedMessageCount(1);
        error.expectedBodiesReceived("The Body");
        
        template.requestBody("direct:start", "The Body");

        result.assertIsSatisfied();
        error.assertIsSatisfied();
    }

    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", new MyBean());
        return answer;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error"));
                
                onException(MyCustomException.class).
                    maximumRedeliveries(0).
                    handled(true).
                    bean("myBean", "handleException").
                    to("mock:error");
                
                from("direct:start").
                    bean("myBean", "throwException").
                    to("mock:result");
            }
        };
    }

    public static class MyBean {

        public void throwException() throws MyCustomException {
            throw new MyCustomException("I'm being thrown!!");
        }       
        
        public void handleException(@ExchangeException Exception exception) {
            assertNotNull(exception);
            assertEquals("I'm being thrown!!", exception.getMessage());
        }
    }
}
