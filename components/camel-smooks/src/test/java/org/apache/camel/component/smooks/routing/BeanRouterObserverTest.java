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
package org.apache.camel.component.smooks.routing;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smooks.api.bean.lifecycle.BeanContextLifecycleEvent;
import org.smooks.api.bean.lifecycle.BeanLifecycle;
import org.smooks.engine.bean.repository.DefaultBeanId;
import org.smooks.testkit.MockExecutionContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link BeanRouterObserver}
 *
 * @author Daniel Bevenius
 */
public class BeanRouterObserverTest extends CamelTestSupport {
    private static final String ENDPOINT_URI = "mock://beanRouterUnitTest";
    private MockEndpoint endpoint;

    @BeforeEach
    public void beforeEach() {
        endpoint = getMockEndpoint(ENDPOINT_URI);
    }

    @Test
    public void onBeanLifecycleEventCreated() throws Exception {
        final String sampleBean = "testOrder";
        final String beanId = "orderId";
        final BeanRouter beanRouter = new BeanRouter(context);

        beanRouter.setBeanId(beanId);
        beanRouter.setToEndpoint(ENDPOINT_URI);
        beanRouter.postConstruct();

        final BeanRouterObserver beanRouterObserver = new BeanRouterObserver(beanRouter, beanId);
        final MockExecutionContext smooksExecutionContext = new MockExecutionContext();
        final BeanContextLifecycleEvent event = mock(BeanContextLifecycleEvent.class);

        when(event.getBeanId()).thenReturn(new DefaultBeanId(null, 0, beanId));
        when(event.getLifecycle()).thenReturn(BeanLifecycle.END_FRAGMENT);
        when(event.getBean()).thenReturn(sampleBean);
        when(event.getExecutionContext()).thenReturn(smooksExecutionContext);

        endpoint.setExpectedMessageCount(1);
        beanRouterObserver.onBeanLifecycleEvent(event);
        endpoint.assertIsSatisfied();
        endpoint.expectedBodiesReceived(sampleBean);
    }

}
