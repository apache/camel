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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.log.LogComponent;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.LazyStartProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.Test;

public class LazyStartProducerTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testLazyStartProducer() throws Exception {
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Lazy Producer", "Hello Again Lazy Producer");

        LazyStartProducer lazy = new LazyStartProducer(mock);
        assertFalse(ServiceHelper.isStarted(lazy));
        assertFalse(ServiceHelper.isStarted(lazy.getProcessor()));
        assertEquals(mock.isSingleton(), lazy.isSingleton());

        ServiceHelper.startService(lazy);
        assertTrue(ServiceHelper.isStarted(lazy));
        assertFalse(ServiceHelper.isStarted(lazy.getProcessor()));
        assertEquals(mock.isSingleton(), lazy.isSingleton());

        // process a message which should start the delegate
        Exchange exchange = mock.createExchange();
        exchange.getIn().setBody("Hello Lazy Producer");
        lazy.process(exchange);
        assertTrue(ServiceHelper.isStarted(lazy));
        assertTrue(ServiceHelper.isStarted(lazy.getProcessor()));
        assertEquals(mock.isSingleton(), lazy.isSingleton());

        // process a message which should start the delegate
        exchange = mock.createExchange();
        exchange.getIn().setBody("Hello Again Lazy Producer");
        lazy.process(exchange);
        assertTrue(ServiceHelper.isStarted(lazy));
        assertTrue(ServiceHelper.isStarted(lazy.getProcessor()));
        assertEquals(mock.isSingleton(), lazy.isSingleton());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void lazyStartProducerGlobal() throws Exception {
        context.getGlobalEndpointConfiguration().setLazyStartProducer(true);

        MockEndpoint mock = getMockEndpoint("mock:result");
        assertTrue(mock.isLazyStartProducer());

        LogEndpoint log = getMandatoryEndpoint("log:foo", LogEndpoint.class);
        assertTrue(log.isLazyStartProducer());
    }

    @Test
    public void lazyStartProducerComponent() throws Exception {
        context.getComponent("log", LogComponent.class).setLazyStartProducer(true);

        LogEndpoint log = getMandatoryEndpoint("log:foo", LogEndpoint.class);
        assertTrue(log.isLazyStartProducer());

        // but mock is false
        MockEndpoint mock = getMockEndpoint("mock:result");
        assertFalse(mock.isLazyStartProducer());

        // but we can override this via parameter
        MockEndpoint mock2 = getMockEndpoint("mock:foo?lazyStartProducer=true");
        assertTrue(mock2.isLazyStartProducer());
    }

}
