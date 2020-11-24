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
package org.apache.camel.support;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultComponentTest extends ContextTestSupport {
    @Test
    public void testResolveRawDefault() {
        context.addComponent("dummy", new MyComponent());
        MyEndpoint endpoint = context.getEndpoint("dummy:test?test=RAW(value)", MyEndpoint.class);
        assertEquals("value", endpoint.getParameters().get("test"));
    }

    @Test
    public void testResolveRawTrue() {
        context.addComponent("dummy", new MyComponent(true));
        MyEndpoint endpoint = context.getEndpoint("dummy:test?test=RAW(value)", MyEndpoint.class);
        assertEquals("value", endpoint.getParameters().get("test"));
    }

    @Test
    public void testResolveRawFalse() {
        context.addComponent("dummy", new MyComponent(false));
        MyEndpoint endpoint = context.getEndpoint("dummy:test?test=RAW(value)", MyEndpoint.class);
        assertEquals("RAW(value)", endpoint.getParameters().get("test"));
    }

    private static class MyComponent extends DefaultComponent {
        private final Boolean raw;

        public MyComponent() {
            this(null);
        }

        public MyComponent(Boolean raw) {
            this.raw = raw;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            MyEndpoint answer = new MyEndpoint(parameters);
            parameters.clear();

            return answer;
        }

        @Override
        protected boolean resolveRawParameterValues() {
            return raw != null ? raw : super.resolveRawParameterValues();
        }
    }

    private static class MyEndpoint extends DefaultEndpoint {
        private final Map<String, Object> parameters;

        public MyEndpoint(Map<String, Object> parameters) {
            this.parameters = new HashMap<>(parameters);
        }

        @Override
        public Producer createProducer() throws Exception {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return false;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }
    }
}
