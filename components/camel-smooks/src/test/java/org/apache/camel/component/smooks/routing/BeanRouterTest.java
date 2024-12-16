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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.smooks.SmooksConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smooks.Smooks;
import org.smooks.api.ExecutionContext;
import org.smooks.api.SmooksException;
import org.smooks.api.bean.context.BeanContext;
import org.smooks.api.bean.lifecycle.BeanLifecycle;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.engine.bean.lifecycle.DefaultBeanContextLifecycleEvent;
import org.smooks.engine.injector.Scope;
import org.smooks.engine.lifecycle.PostConstructLifecyclePhase;
import org.smooks.engine.lookup.LifecycleManagerLookup;
import org.smooks.engine.resource.config.DefaultResourceConfig;
import org.smooks.testkit.MockApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link BeanRouter}.
 *
 */
public class BeanRouterTest extends CamelTestSupport {
    private static final String END_POINT_URI = "mock://beanRouterUnitTest";
    private static final String BEAN_ID = "testBeanId";

    private ExecutionContext smooksExecutionContext;
    private MockEndpoint endpoint;
    private MyBean myBean = new MyBean("bajja");
    private BeanContext beanContext;

    @Test
    public void visitAfter() throws Exception {
        endpoint.setExpectedMessageCount(1);
        endpoint.expectedBodiesReceived(myBean);
        createBeanRouter(BEAN_ID, END_POINT_URI).visitAfter(null, smooksExecutionContext);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testVisitAfterWithMissingBeanInSmookBeanContext() throws SmooksException {
        when(beanContext.getBean(BEAN_ID)).thenReturn(null);
        assertThrows(SmooksException.class,
                () -> createBeanRouter(BEAN_ID, END_POINT_URI).visitAfter(null, smooksExecutionContext));
    }

    @Test
    public void testBeanRouterUsingOnlyBeanId() throws Exception {
        endpoint.setExpectedMessageCount(1);
        endpoint.expectedBodiesReceived(myBean);

        Smooks smooks = new Smooks();
        ExecutionContext executionContext = smooks.createExecutionContext();

        BeanRouter beanRouter = createBeanRouter(null, BEAN_ID, END_POINT_URI);
        beanRouter.onPreExecution(executionContext);
        executionContext.getBeanContext().addBean(BEAN_ID, myBean);

        // Force an END event
        executionContext.getBeanContext().notifyObservers(new DefaultBeanContextLifecycleEvent(
                executionContext,
                null, BeanLifecycle.END_FRAGMENT, executionContext.getBeanContext().getBeanId(BEAN_ID), myBean));

        endpoint.assertIsSatisfied();
    }

    @Test
    public void testBeanRouterAddsCamelSmooksExecutionContextHeader() throws Exception {
        Smooks smooks = new Smooks();
        ExecutionContext executionContext = smooks.createExecutionContext();

        endpoint.setExpectedMessageCount(1);
        endpoint.expectedHeaderReceived(SmooksConstants.SMOOKS_EXECUTION_CONTEXT, executionContext);

        BeanRouter beanRouter = createBeanRouter(null, BEAN_ID, END_POINT_URI);
        beanRouter.onPreExecution(executionContext);
        executionContext.getBeanContext().addBean(BEAN_ID, myBean);

        // Force an END event
        executionContext.getBeanContext().notifyObservers(new DefaultBeanContextLifecycleEvent(
                executionContext,
                null, BeanLifecycle.END_FRAGMENT, executionContext.getBeanContext().getBeanId(BEAN_ID), myBean));

        endpoint.assertIsSatisfied();
        assertNotNull(endpoint.getReceivedExchanges().get(0).getMessage()
                .getHeader(SmooksConstants.SMOOKS_EXECUTION_CONTEXT, ExecutionContext.class).getBeanContext().getBean(BEAN_ID));
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        endpoint = createAndConfigureMockEndpoint(END_POINT_URI);
        Exchange exchange = createExchange(endpoint);
        BeanContext beanContext = createBeanContextAndSetBeanInContext(BEAN_ID, myBean);

        smooksExecutionContext = createExecutionContext();
        makeExecutionContextReturnBeanContext(beanContext);
    }

    private MockEndpoint createAndConfigureMockEndpoint(String endpointUri) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(endpointUri);
        return mockEndpoint;
    }

    private Exchange createExchange(MockEndpoint endpoint) {
        Exchange exchange = endpoint.createExchange();
        return exchange;
    }

    private BeanContext createBeanContextAndSetBeanInContext(String beanId, Object bean) {
        beanContext = mock(BeanContext.class);
        when(beanContext.getBean(beanId)).thenReturn(bean);
        return beanContext;
    }

    private ExecutionContext createExecutionContext() {
        return mock(ExecutionContext.class);
    }

    private void makeExecutionContextReturnBeanContext(BeanContext beanContext) {
        when(smooksExecutionContext.getBeanContext()).thenReturn(beanContext);
    }

    private BeanRouter createBeanRouter(String beanId, String endpointUri) {
        return createBeanRouter("dummySelector", beanId, endpointUri);
    }

    private BeanRouter createBeanRouter(String selector, String beanId, String endpointUri) {
        BeanRouter beanRouter = new BeanRouter();
        ResourceConfig resourceConfig = new DefaultResourceConfig();
        if (selector != null) {
            resourceConfig.setSelector(selector, new Properties());
        }
        resourceConfig.setParameter("beanId", beanId);
        resourceConfig.setParameter("toEndpoint", endpointUri);

        MockApplicationContext appContext = new MockApplicationContext();
        appContext.getRegistry().registerObject(CamelContext.class, context);
        appContext.getRegistry().lookup(new LifecycleManagerLookup()).applyPhase(beanRouter,
                new PostConstructLifecyclePhase(new Scope(appContext.getRegistry(), resourceConfig, beanId)));

        return beanRouter;
    }

    public record MyBean(String name) {
    }
}
