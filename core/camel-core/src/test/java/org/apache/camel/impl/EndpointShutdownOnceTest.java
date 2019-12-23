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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test that endpoints are only shutdown once when CamelContext is stopping.
 */
public class EndpointShutdownOnceTest extends Assert {

    @Test
    public void testEndpointShutdown() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("my", new MyComponent());
        context.start();

        MyEndpoint my = context.getEndpoint("my:foo", MyEndpoint.class);

        assertTrue("Should be started", my.getStatus().isStarted());

        context.stop();
        assertFalse("Should not be started", my.getStatus().isStarted());
        assertTrue("Should be stopped", my.getStatus().isStopped());

        assertEquals("Should only shutdown once", 1, my.getInvoked());
    }

    private static final class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyEndpoint(uri, this);
        }
    }

    private static final class MyEndpoint extends DefaultEndpoint {

        private volatile int invoked;

        private MyEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        public int getInvoked() {
            return invoked;
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
            return true;
        }

        @Override
        protected void doShutdown() throws Exception {
            super.doShutdown();
            invoked++;
        }
    }
}
