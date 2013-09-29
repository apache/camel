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

/**
 * Unit test for reference properties
 */
public class DefaultComponentReferencePropertiesTest extends ContextTestSupport {

    public final class MyEndpoint extends DefaultEndpoint {

        private Expression expression;
        private String stringExpression;
        private String name;
        private Expression special;

        private MyEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        public boolean isSingleton() {
            return true;
        }

        public Producer createProducer() throws Exception {
            return null;
        }

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

    public final class MyComponent extends DefaultComponent {

        private MyComponent(CamelContext context) {
            super(context);
        }

        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            MyEndpoint result = new MyEndpoint(uri, this);
            setProperties(result, parameters);
            return result;
        }

    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myExpression", ExpressionBuilder.bodyExpression());
        return jndi;
    }

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

    public void testOnlyStringSetter() throws Exception {
        MyComponent component = new MyComponent(context);
        MyEndpoint endpoint = (MyEndpoint) component.createEndpoint("foo://?name=Claus");
        assertNotNull(endpoint);
        assertEquals("Claus", endpoint.name);
        assertNull(endpoint.expression);
        assertNull(endpoint.stringExpression);
    }

    public void testCallStringSetter() throws Exception {
        MyComponent component = new MyComponent(context);
        MyEndpoint endpoint = (MyEndpoint) component.createEndpoint("foo://?expression=hello");
        assertNotNull(endpoint);
        assertEquals("hello", endpoint.stringExpression);
        assertNull(endpoint.expression);
        assertNull(endpoint.name);
    }

    public void testNoBeanInRegistryThenCallStringSetter() throws Exception {
        MyComponent component = new MyComponent(context);
        MyEndpoint endpoint = (MyEndpoint) component.createEndpoint("foo://?expression=#hello");
        assertNotNull(endpoint);
        assertEquals("#hello", endpoint.stringExpression);
        assertNull(endpoint.expression);
        assertNull(endpoint.name);
    }

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

    public void testTypoInParameter() throws Exception {
        MyComponent component = new MyComponent(context);
        try {
            component.createEndpoint("foo://?xxxexpression=#hello");
            fail("Should have throw a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            // ok
        }
    }

    public void testTypoInParameterValue() throws Exception {
        MyComponent component = new MyComponent(context);
        try {
            component.createEndpoint("foo://?special=#dummy");
            fail("Should have throw a IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

}