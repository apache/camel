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
package org.apache.camel.component.jbpm.workitem;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.drools.core.process.instance.impl.WorkItemImpl;
import org.jbpm.bpmn2.handler.WorkItemHandlerRuntimeException;
import org.jbpm.process.workitem.core.TestWorkItemManager;
import org.jbpm.services.api.service.ServiceRegistry;
import org.junit.Test;
import org.kie.api.runtime.process.WorkItemHandler;

import static org.hamcrest.CoreMatchers.*;

//http://camel.apache.org/using-getin-or-getout-methods-on-exchange.html
//http://camel.apache.org/async.html
public class CamelWorkItemHandlerIntegrationTests extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Test
    public void testSyncInOnly() throws Exception {
        // Setup
        String routeId = "testSyncInOnlyExceptionRoute";
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId(routeId)
                        .setBody(simple("${body.getParameter(\"Request\")}"))
                        .to("mock:result");
            }
        };
        context.addRoutes(builder);
        try {
            // Register the Camel Context with the jBPM ServiceRegistry.
            ServiceRegistry.get().register(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY, context);

            // Test
            String expectedBody = "helloRequest";
            resultEndpoint.expectedBodiesReceived(expectedBody);

            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, "start");
            workItem.setParameter("Request", expectedBody);

            TestWorkItemManager manager = new TestWorkItemManager();

            WorkItemHandler handler = new InOnlyCamelWorkItemHandler();

            handler.executeWorkItem(workItem, manager);

            // Assertions
            assertThat(manager.getResults().size(), equalTo(0));
            resultEndpoint.assertIsSatisfied();
        } finally {
            // Cleanup
            context.removeRoute(routeId);
            ServiceRegistry.get().remove(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY);
        }
    }

    @Test(expected = WorkItemHandlerRuntimeException.class)
    public void testSyncInOnlyException() throws Exception {
        // Setup
        String routeId = "testSyncInOnlyExceptionRoute";
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId(routeId)
                        .setBody(simple("${body.getParameter(\"Request\")}"))
                        .throwException(new IllegalArgumentException("Illegal contennt!"))
                        .to("mock:result");
            }
        };
        context.addRoutes(builder);
        try {
            // Register the Camel Context with the jBPM ServiceRegistry.
            ServiceRegistry.get().register(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY, context);

            // Test
            String expectedBody = "helloRequest";
            resultEndpoint.expectedBodiesReceived(expectedBody);

            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, "start");
            workItem.setParameter("Request", expectedBody);

            TestWorkItemManager manager = new TestWorkItemManager();

            WorkItemHandler handler = new InOnlyCamelWorkItemHandler();

            handler.executeWorkItem(workItem, manager);

            // Assertions
            assertThat(manager.getResults().size(), equalTo(0));
            resultEndpoint.assertIsSatisfied();
        } finally {
            // Cleanup
            context.removeRoute(routeId);
            ServiceRegistry.get().remove(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY);
        }
    }

    @Test
    public void testSyncInOut() throws Exception {
        // Setup
        String routeId = "testSyncInOnlyExceptionRoute";
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId(routeId)
                        .setBody(simple("${body.getParameter(\"Request\")}"))
                        .to("mock:result");
            }
        };
        context.addRoutes(builder);
        try {
            // Register the Camel Context with the jBPM ServiceRegistry.
            ServiceRegistry.get().register(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY, context);

            // Test
            String expectedBody = "helloRequest";
            resultEndpoint.expectedBodiesReceived(expectedBody);

            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, "start");
            workItem.setParameter("Request", expectedBody);

            TestWorkItemManager manager = new TestWorkItemManager();

            AbstractCamelWorkItemHandler handler = new InOutCamelWorkItemHandler();

            handler.executeWorkItem(workItem, manager);

            // Assertions
            assertThat(manager.getResults().size(), equalTo(1));
            resultEndpoint.assertIsSatisfied();
        } finally {
            // Cleanup
            context.removeRoute(routeId);
            ServiceRegistry.get().remove(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY);
        }

    }

    @Test(expected = WorkItemHandlerRuntimeException.class)
    public void testSyncInOutException() throws Exception {
        // Setup
        String routeId = "testSyncInOutExceptionRoute";
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId(routeId)
                        .setBody(simple("${body.getParameter(\"Request\")}"))
                        .throwException(new IllegalArgumentException("Illegal contennt!"))
                        .to("mock:result");
            }
        };
        context.addRoutes(builder);
        try {
            // Register the Camel Context with the jBPM ServiceRegistry.
            ServiceRegistry.get().register(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY, context);

            // Test
            String expectedBody = "helloRequest";
            resultEndpoint.expectedBodiesReceived(expectedBody);

            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, "start");
            workItem.setParameter("Request", expectedBody);

            TestWorkItemManager manager = new TestWorkItemManager();

            WorkItemHandler handler = new InOutCamelWorkItemHandler();

            handler.executeWorkItem(workItem, manager);
        } finally {
            // Cleanup
            context.removeRoute(routeId);
            ServiceRegistry.get().remove(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY);
        }
    }


    @Test(expected = IllegalArgumentException.class)
    public void testSyncInOutDontHandleException() throws Exception {
        // Setup
        String routeId = "testSyncInOutExceptionRoute";
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId(routeId)
                        .setBody(simple("${body.getParameter(\"Request\")}"))
                        .throwException(new IllegalArgumentException("Illegal contennt!"))
                        .to("mock:result");
            }
        };
        context.addRoutes(builder);
        try {
            // Register the Camel Context with the jBPM ServiceRegistry.
            ServiceRegistry.get().register(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY, context);

            // Test
            String expectedBody = "helloRequest";
            resultEndpoint.expectedBodiesReceived(expectedBody);

            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, "start");
            workItem.setParameter("Request", expectedBody);
            workItem.setParameter("HandleExceptions", false);

            TestWorkItemManager manager = new TestWorkItemManager();

            WorkItemHandler handler = new InOutCamelWorkItemHandler();

            handler.executeWorkItem(workItem, manager);
        } finally {
            // Cleanup
            context.removeRoute(routeId);
            ServiceRegistry.get().remove(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY);
        }
    }

    @Test(expected = RuntimeCamelException.class)
    public void testSyncInOutCamelHandleException() throws Exception {
        // Setup
        String routeId = "testSyncInOutExceptionRoute";
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId(routeId)
                        .setBody(simple("${body.getParameter(\"Request\")}"))
                        .doTry()
                            .throwException(new IllegalArgumentException("Illegal contennt!"))
                        .doCatch(IllegalArgumentException.class)
                            .process(new Processor() {
                            
                                @Override
                                public void process(Exchange exchange) throws Exception {
                                    RuntimeCamelException exceptionWrapper = new RuntimeCamelException(Exchange.EXCEPTION_CAUGHT);        
                                    throw exceptionWrapper;
                                }
                            })
                        .end()
                        .to("mock:result");
            }
        };
        context.addRoutes(builder);
        try {
            // Register the Camel Context with the jBPM ServiceRegistry.
            ServiceRegistry.get().register(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY, context);

            // Test
            String expectedBody = "helloRequest";
            resultEndpoint.expectedBodiesReceived(expectedBody);

            WorkItemImpl workItem = new WorkItemImpl();
            workItem.setParameter(JBPMConstants.CAMEL_ENDPOINT_ID_WI_PARAM, "start");
            workItem.setParameter("Request", expectedBody);
            workItem.setParameter("HandleExceptions", false);

            TestWorkItemManager manager = new TestWorkItemManager();

            WorkItemHandler handler = new InOutCamelWorkItemHandler();

            handler.executeWorkItem(workItem, manager);
        } finally {
            // Cleanup
            context.removeRoute(routeId);
            ServiceRegistry.get().remove(JBPMConstants.GLOBAL_CAMEL_CONTEXT_SERVICE_KEY);
        }
    }

}
