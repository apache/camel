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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.VariableRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.spi.VariableRepositoryFactory.GLOBAL_VARIABLE_REPOSITORY_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CustomGlobalVariableTest extends ContextTestSupport {
    private MockEndpoint end;
    private String variableName = "foo";
    private String expectedVariableValue = "bar";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind(GLOBAL_VARIABLE_REPOSITORY_ID, new MyGlobalRepo());
        return context;
    }

    @Test
    public void testSetExchangeVariableMidRoute() throws Exception {
        assertNull(context.getVariable(variableName));

        end.expectedMessageCount(1);

        template.sendBody("direct:start", "<blah/>");

        // make sure we got the message
        assertMockEndpointsSatisfied();

        // lets get the variable value
        List<Exchange> exchanges = end.getExchanges();
        Exchange exchange = exchanges.get(0);
        String actualVariableValue = exchange.getVariable(variableName, String.class);
        // should be stored on global so null
        assertNull(actualVariableValue);

        // should be stored as global variable
        assertEquals("!" + expectedVariableValue + "!", context.getVariable(variableName));
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        end = getMockEndpoint("mock:end");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // stored as global variable
                from("direct:start").setVariable("global:" + variableName).constant(expectedVariableValue).to("mock:end");
            }
        };
    }

    private static class MyGlobalRepo extends ServiceSupport implements VariableRepository {

        private Object value;

        @Override
        public String getId() {
            return "myGlobal";
        }

        @Override
        public Object getVariable(String name) {
            if (value != null) {
                return "!" + value + "!";
            }
            return null;
        }

        @Override
        public void setVariable(String name, Object value) {
            this.value = value;
        }

        @Override
        public Object removeVariable(String name) {
            return null;
        }
    }
}
