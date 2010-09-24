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

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

/**
 * @version $Revision: 882486 $
 */
public class SimpleScheduledRoutePolicyTest extends CamelTestSupport {
    private static final transient Log LOG = LogFactory.getLog(SimpleScheduledRoutePolicyTest.class);
    
    /* (non-Javadoc)
     * @see org.apache.camel.test.junit4.CamelTestSupport#s;etUp()
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testScheduledStartRoutePolicy() throws Exception {
        MockEndpoint success = (MockEndpoint) context.getEndpoint("mock:success");        
        
        success.expectedMessageCount(1);
        
        context.addRoutes(new RouteBuilder() {
            public void configure() {   
                SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy("org/apache/camel/routepolicy/quartz/myquartz.properties");
                long startTime = System.currentTimeMillis() + 3000L;
                policy.setRouteStartDate(new Date(startTime));
                policy.setRouteStartRepeatCount(1);
                policy.setRouteStartRepeatInterval(3000);
                
                from("direct:start")
                    .routeId("test")
                    .routePolicy(policy)
                    .to("mock:success");
            }
        });
        context.start();
        context.stopRoute("test", 0, TimeUnit.MILLISECONDS);
        
        Thread.currentThread().sleep(4000);
        assertTrue(context.getRouteStatus("test") == ServiceStatus.Started);
        template.sendBody("direct:start", "Ready or not, Here, I come");

        success.assertIsSatisfied();
    }

    @Test
    public void testScheduledStopRoutePolicy() throws Exception {
        boolean consumerStopped = false;
  
        MockEndpoint unreachable = (MockEndpoint) context.getEndpoint("mock:unreachable");        
        
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                long startTime = System.currentTimeMillis() + 3000;
                policy.setRouteStopDate(new Date(startTime));
                policy.setRouteStopRepeatCount(1);
                policy.setRouteStopRepeatInterval(3000);
                
                from("direct:start")
                    .routeId("test")
                    .routePolicy(policy)
                    .to("mock:unreachable");
            }
        });
        context.start();
        
        Thread.currentThread().sleep(4000);

        assertTrue(context.getRouteStatus("test") == ServiceStatus.Stopped);
        
        try {
            template.sendBody("direct:start", "Ready or not, Here, I come");
        } catch (CamelExecutionException e) {
            consumerStopped = true;
        }    
        assertTrue(consumerStopped);
    } 
    
    @Test
    public void testScheduledSuspendRoutePolicy() throws Exception {
        boolean consumerSuspended = false;
  
        MockEndpoint unreachable = (MockEndpoint) context.getEndpoint("mock:unreachable");        
        
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                long startTime = System.currentTimeMillis() + 3000L;
                policy.setRouteSuspendDate(new Date(startTime));
                policy.setRouteSuspendRepeatCount(1);
                policy.setRouteSuspendRepeatInterval(3000);
                
                from("direct:start")
                    .routeId("test")
                    .routePolicy(policy)
                    .to("mock:unreachable");
            } 
        });
        context.start();
        
        Thread.currentThread().sleep(4000);
        try {
            template.sendBody("direct:start", "Ready or not, Here, I come");
        } catch (CamelExecutionException e) {
            consumerSuspended = true;
        }        
        assertTrue(consumerSuspended);
    }    
    
    @Test
    public void testScheduledResumeRoutePolicy() throws Exception {
  
        MockEndpoint success = (MockEndpoint) context.getEndpoint("mock:success");        
        
        success.expectedMessageCount(1);
        
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                long startTime = System.currentTimeMillis() + 3000L;
                policy.setRouteResumeDate(new Date(startTime));
                policy.setRouteResumeRepeatCount(1);
                policy.setRouteResumeRepeatInterval(3000);
                
                from("direct:start")
                    .routeId("test")
                    .routePolicy(policy)
                    .to("mock:success");
            } 
        });
        context.start();
        ServiceHelper.suspendService(context.getRoute("test").getConsumer());
        try {
            template.sendBody("direct:start", "Ready or not, Here, I come");
        } catch (CamelExecutionException e) {
            LOG.debug("Consumer successfully suspended");
        } 
        
        Thread.currentThread().sleep(4000);
        template.sendBody("direct:start", "Ready or not, Here, I come");
        
        success.assertIsSatisfied();
    } 
    
}
