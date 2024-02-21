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
package org.apache.camel.component.bean;

import java.util.concurrent.Callable;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class BeanReturnCallableReturnNullTest extends ContextTestSupport {

    @Test
    public void testBeanReturnCallable() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("StartMyBean");
        mock.expectedHeaderReceived("user", "admin");
        mock.expectedHeaderReceived("foo", "bar");

        template.requestBody("direct:in", "Start");

        mock.assertIsSatisfied();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("myBean", new MyBean());
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").setHeader("foo", constant("bar")).to("bean:myBean").to("mock:result");
            }
        };
    }

    public static class MyBean {

        public Callable<Object> doSomething(final Exchange exchange) {
            return () -> {
                String body = exchange.getIn().getBody(String.class);
                exchange.getIn().setHeader("user", "admin");
                exchange.getIn().setBody(body + "MyBean");
                // return null as we have set changes already
                return null;
            };
        }
    }

}
