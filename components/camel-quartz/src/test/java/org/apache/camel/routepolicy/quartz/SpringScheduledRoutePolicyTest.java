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
import org.apache.camel.test.junit4.TestSupport;
import org.apache.camel.util.ServiceHelper;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public abstract class SpringScheduledRoutePolicyTest extends TestSupport {
    protected enum TestType {
        SIMPLE, CRON
    }
    private ClassPathXmlApplicationContext applicationContext;
    private TestType testType;       
    
    public void startTest() throws Exception {
        CamelContext context = startRouteWithPolicy("startPolicy");
        
        MockEndpoint mock = context.getEndpoint("mock:success", MockEndpoint.class);
        mock.expectedMinimumMessageCount(1);
        
        context.stopRoute("testRoute", 1000, TimeUnit.MILLISECONDS);
        
        Thread.sleep(4000);
        assertTrue(context.getRouteStatus("testRoute") == ServiceStatus.Started);
        context.createProducerTemplate().sendBody("direct:start", "Ready or not, Here, I come");

        context.stop();
        mock.assertIsSatisfied();
    }

    
    public void stopTest() throws Exception {
        boolean consumerStopped = false;
        
        CamelContext context = startRouteWithPolicy("stopPolicy");
        
        Thread.sleep(4000);
        assertTrue(context.getRouteStatus("testRoute") == ServiceStatus.Stopped);
        try {
            context.createProducerTemplate().sendBody("direct:start", "Ready or not, Here, I come");
        } catch (CamelExecutionException e) {
            consumerStopped = true;
        }    
        context.stop();
        assertTrue(consumerStopped);
    }
    
    
    public void suspendTest() throws Exception {
        boolean consumerSuspended = false;

        CamelContext context = startRouteWithPolicy("suspendPolicy");
        
        Thread.sleep(4000);
        try {
            context.createProducerTemplate().sendBody("direct:start", "Ready or not, Here, I come");
        } catch (CamelExecutionException e) {
            consumerSuspended = true;
        }        
        
        context.stop();
        assertTrue(consumerSuspended);
    }
    
    public void resumeTest() throws Exception {
        CamelContext context = startRouteWithPolicy("resumePolicy");
        
        MockEndpoint mock = context.getEndpoint("mock:success", MockEndpoint.class);
        mock.expectedMinimumMessageCount(1);

        ServiceHelper.suspendService(context.getRoute("testRoute").getConsumer());
        
        Thread.sleep(4000);
        context.createProducerTemplate().sendBody("direct:start", "Ready or not, Here, I come");
        
        context.stop();
        mock.assertIsSatisfied();
    }
    
    @SuppressWarnings("unchecked")
    private CamelContext startRouteWithPolicy(String policyBeanName) throws Exception {
        CamelContext context = new DefaultCamelContext();
        List<RouteDefinition> routes = (List<RouteDefinition>)applicationContext.getBean("testRouteContext");
        RoutePolicy policy = applicationContext.getBean(policyBeanName, RoutePolicy.class);
        assertTrue(getTestType() == TestType.SIMPLE 
            ? policy instanceof SimpleScheduledRoutePolicy 
            : policy instanceof CronScheduledRoutePolicy);
        routes.get(0).routePolicy(policy);
        ((ModelCamelContext)context).addRouteDefinitions(routes);
        context.start();
        return context;
    }
    
    public ClassPathXmlApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ClassPathXmlApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public TestType getTestType() {
        return testType;
    }

    public void setTestType(TestType testType) {
        this.testType = testType;
    }    
    
}
