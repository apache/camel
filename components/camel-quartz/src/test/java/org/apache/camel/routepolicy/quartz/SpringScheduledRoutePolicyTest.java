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
package org.apache.camel.routepolicy.quartz;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.support.service.ServiceHelper;
import org.awaitility.Awaitility;
import org.springframework.context.support.AbstractXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class SpringScheduledRoutePolicyTest {
    protected enum TestType {
        SIMPLE,
        CRON
    }

    private AbstractXmlApplicationContext applicationContext;
    private TestType testType;

    public abstract void setUp();

    public void startTest() throws Exception {
        setUp();

        CamelContext context = startRouteWithPolicy("startPolicy");

        MockEndpoint mock = context.getEndpoint("mock:success", MockEndpoint.class);
        mock.expectedMinimumMessageCount(1);

        context.getRouteController().stopRoute("testRoute", 1000, TimeUnit.MILLISECONDS);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                () -> assertSame(ServiceStatus.Started, context.getRouteController().getRouteStatus("testRoute")));

        context.createProducerTemplate().sendBody("direct:start?timeout=1000", "Ready or not, Here, I come");

        context.stop();
        mock.assertIsSatisfied();
    }

    public void stopTest() throws Exception {
        setUp();

        CamelContext context = startRouteWithPolicy("stopPolicy");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                () -> assertSame(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("testRoute")));

        assertThrows(CamelExecutionException.class,
                () -> context.createProducerTemplate().sendBody("direct:start?timeout=1000", "Ready or not, Here, I come"));

        context.stop();
    }

    public void suspendTest() throws Exception {
        setUp();

        CamelContext context = startRouteWithPolicy("suspendPolicy");

        // wait for route to suspend
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(ServiceHelper.isSuspended(context.getRoute("testRoute").getConsumer())));

        assertThrows(CamelExecutionException.class,
                () -> context.createProducerTemplate().sendBody("direct:start?timeout=1000", "Ready or not, Here, I come"));

        context.stop();
    }

    public void resumeTest() throws Exception {
        setUp();

        CamelContext context = startRouteWithPolicy("resumePolicy");

        MockEndpoint mock = context.getEndpoint("mock:success", MockEndpoint.class);
        mock.expectedMinimumMessageCount(1);

        ServiceHelper.suspendService(context.getRoute("testRoute").getConsumer());

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(ServiceHelper.isStarted(context.getRoute("testRoute").getConsumer())));

        context.createProducerTemplate().sendBody("direct:start?timeout=1000", "Ready or not, Here, I come");

        context.stop();
        mock.assertIsSatisfied();
    }

    @SuppressWarnings("unchecked")
    private CamelContext startRouteWithPolicy(String policyBeanName) throws Exception {
        CamelContext context = new DefaultCamelContext();
        List<RouteDefinition> routes = (List<RouteDefinition>) applicationContext.getBean("testRouteContext");
        RoutePolicy policy = applicationContext.getBean(policyBeanName, RoutePolicy.class);
        assertTrue(getTestType() == TestType.SIMPLE
                ? policy instanceof SimpleScheduledRoutePolicy
                : policy instanceof CronScheduledRoutePolicy);
        routes.get(0).routePolicy(policy);
        ((ModelCamelContext) context).addRouteDefinitions(routes);
        context.start();
        return context;
    }

    public AbstractXmlApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(AbstractXmlApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public TestType getTestType() {
        return testType;
    }

    public void setTestType(TestType testType) {
        this.testType = testType;
    }

}
