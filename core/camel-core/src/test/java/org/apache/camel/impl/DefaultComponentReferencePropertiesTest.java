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
package org.apache.camel.impl;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for reference properties
 */
public class DefaultComponentReferencePropertiesTest extends ContextTestSupport {

    public static final class MyEndpoint extends DefaultEndpoint {

        private Expression expression;
        private String stringExpression;
        private String name;
        private Expression special;

        private MyEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public Producer createProducer() throws Exception {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        public void setExpression(List<?> expressions) {
            // do nothing
        }

        public void setExpression(Expression expression) {
            this.expression = expression;
        }

        public void setExpression(String expression) {
            stringExpression = expression;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSpecial(Expression special) {
            this.special = special;
        }
    }

    public static final class MyComponent extends DefaultComponent {

        private MyComponent(CamelContext context) {
            super(context);
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            MyEndpoint result = new MyEndpoint(uri, this);
            setProperties(result, parameters);
            return result;
        }

    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("myExpression", ExpressionBuilder.bodyExpression());
        return registry;
    }

    @Test
    public void testEmptyPath() throws Exception {
        DefaultComponent component = new DefaultComponent(context) {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
                assertEquals("foo://?name=Christian", uri);
                assertEquals("", remaining);
                assertEquals(1, parameters.size());
                assertEquals("Christian", parameters.get("name"));

                return null;
            }

        };
        component.createEndpoint("foo://?name=Christian");
    }

    @Test
    public void testOnlyStringSetter() throws Exception {
        MyComponent component = new MyComponent(context);
        MyEndpoint endpoint = (MyEndpoint) component.createEndpoint("foo://?name=Claus");
        assertNotNull(endpoint);
        assertEquals("Claus", endpoint.name);
        assertNull(endpoint.expression);
        assertNull(endpoint.stringExpression);
    }

    @Test
    public void testCallStringSetter() throws Exception {
        MyComponent component = new MyComponent(context);
        MyEndpoint endpoint = (MyEndpoint) component.createEndpoint("foo://?expression=hello");
        assertNotNull(endpoint);
        assertEquals("hello", endpoint.stringExpression);
        assertNull(endpoint.expression);
        assertNull(endpoint.name);
    }

    @Test
    public void testNoBeanInRegistryThenCallStringSetter() throws Exception {
        MyComponent component = new MyComponent(context);
        MyEndpoint endpoint = (MyEndpoint) component.createEndpoint("foo://?expression=#hello");
        assertNotNull(endpoint);
        assertEquals("#hello", endpoint.stringExpression);
        assertNull(endpoint.expression);
        assertNull(endpoint.name);
    }

    @Test
    public void testCallExpressionSetter() throws Exception {
        MyComponent component = new MyComponent(context);
        MyEndpoint endpoint = (MyEndpoint) component.createEndpoint("foo://?expression=#myExpression");
        assertNotNull(endpoint);

        assertNull(endpoint.stringExpression);
        assertNotNull(endpoint.expression);
        assertNull(endpoint.name);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");
        assertEquals("Hello World", endpoint.expression.evaluate(exchange, String.class));
    }

    @Test
    public void testCallSingleExpressionSetter() throws Exception {
        MyComponent component = new MyComponent(context);
        MyEndpoint endpoint = (MyEndpoint) component.createEndpoint("foo://?special=#myExpression");
        assertNotNull(endpoint);

        assertNull(endpoint.stringExpression);
        assertNull(endpoint.expression);
        assertNull(endpoint.name);
        assertNotNull(endpoint.special);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");
        assertEquals("Hello World", endpoint.special.evaluate(exchange, String.class));
    }

    @Test
    public void testTypoInParameter() {
        MyComponent component = new MyComponent(context);

        assertThrows(ResolveEndpointFailedException.class,
                () -> component.createEndpoint("foo://?xxxexpression=#hello"),
                "Should have throw a ResolveEndpointFailedException");
    }

    @Test
    public void testTypoInParameterValue() {
        MyComponent component = new MyComponent(context);

        assertThrows(Exception.class,
                () -> component.createEndpoint("foo://?special=#dummy"),
                "Should have throw a Exception");
    }

}
