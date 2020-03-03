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

import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ComponentUseRawUriTest extends ContextTestSupport {

    public static class MyEndpoint extends DefaultEndpoint {
        String remaining;
        String foo;
        String bar;

        public MyEndpoint(final String uri, Component component, final String remaining) {
            super(uri, component);
            this.remaining = remaining;
        }

        @Override
        public Producer createProducer() throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        public String getBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        public String getUri() {
            return getEndpointUri();
        }
    }

    class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
            MyEndpoint answer = new MyEndpoint(uri, this, remaining);
            setProperties(answer, parameters);
            return answer;
        }

        @Override
        public boolean useRawUri() {
            // we want the raw uri, so our component can understand the endpoint
            // configuration as it was typed
            return true;
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        context.addComponent("my", new MyComponent());
    }

    @Test
    public void testUseRaw() {
        String uri = "my:host:11303/tube1+tube?foo=%2B+tube%3F&bar=++%%w?rd";
        MyEndpoint endpoint = context.getEndpoint(uri, MyEndpoint.class);
        assertNotNull("endpoint", endpoint);

        assertEquals("%2B+tube%3F", endpoint.getFoo());
        assertEquals("++%%w?rd", endpoint.getBar());
        assertEquals(uri, endpoint.getUri());
    }

}
