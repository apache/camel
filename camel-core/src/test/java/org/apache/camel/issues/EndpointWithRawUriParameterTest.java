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

import java.util.List;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;

public class EndpointWithRawUriParameterTest extends ContextTestSupport {

    public final class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            Endpoint answer = new MyEndpoint(uri, this);
            setProperties(answer, parameters);
            return answer;
        }
    }

    public final class MyEndpoint extends DefaultEndpoint {

        private String username;
        private String password;
        private List<String> lines;

        public MyEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public Producer createProducer() throws Exception {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) throws Exception {
                    exchange.getIn().setHeader("username", getUsername());
                    exchange.getIn().setHeader("password", getPassword());
                    exchange.getIn().setHeader("lines", getLines());
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getLines() {
            return lines;
        }

        public void setLines(List<String> lines) {
            this.lines = lines;
        }
    }

    public void testRawUriParameter() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("username", "scott");
        getMockEndpoint("mock:result").expectedHeaderReceived("password", "++%%w?rd)");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testUriParameterLines() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:lines", "Hello World");

        assertMockEndpointsSatisfied();

        List<String> lines = (List<String>) getMockEndpoint("mock:result").getReceivedExchanges().get(0).getIn().getHeader("lines");
        assertEquals(2, lines.size());
        assertEquals("abc", lines.get(0));
        assertEquals("def", lines.get(1));
    }

    public void testRawUriParameterLines() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:rawlines", "Hello World");

        assertMockEndpointsSatisfied();

        List<String> lines = (List<String>) getMockEndpoint("mock:result").getReceivedExchanges().get(0).getIn().getHeader("lines");
        assertEquals(2, lines.size());
        assertEquals("++abc++", lines.get(0));
        assertEquals("++def++", lines.get(1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("mycomponent", new MyComponent());

                from("direct:start")
                    .to("mycomponent:foo?username=scott&password=RAW(++%%w?rd))")
                    .to("mock:result");

                from("direct:lines")
                    .to("mycomponent:foo?lines=abc&lines=def")
                    .to("mock:result");

                from("direct:rawlines")
                    .to("mycomponent:foo?lines=RAW(++abc++)&lines=RAW(++def++)")
                    .to("mock:result");
            }
        };
    }
}
