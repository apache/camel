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
package org.apache.camel.management;

import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

public class JmxNotificationEventNotifierTest extends ContextTestSupport {

    private JmxNotificationEventNotifier notifier;

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // START SNIPPET: e1
        // Set up the JmxNotificationEventNotifier
        notifier = new JmxNotificationEventNotifier();
        notifier.setSource("MyCamel");
        notifier.setIgnoreCamelContextEvents(true);
        notifier.setIgnoreRouteEvents(true);
        notifier.setIgnoreServiceEvents(true);

        CamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(notifier);

        // END SNIPPET: e1
        return context;
    }

    @Test
    public void testExchangeDone() throws Exception {
        // START SNIPPET: e2
        // register the NotificationListener
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=eventnotifiers,name=JmxEventNotifier");
        MyNotificationListener listener = new MyNotificationListener();   
        context.getManagementStrategy().getManagementAgent().getMBeanServer().addNotificationListener(on,
            listener,                                                                                         
            new NotificationFilter() {            
                private static final long serialVersionUID = 1L;

                public boolean isNotificationEnabled(Notification notification) {
                    return notification.getSource().equals("MyCamel");
                }
            }, null);

        // END SNIPPET: e2
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
        
        assertEquals("Get a wrong number of events", 8, listener.getEventCounter());

        context.stop();
    }

    @Test
    public void testExchangeFailed() throws Exception {
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=eventnotifiers,name=JmxEventNotifier");
        
        MyNotificationListener listener = new MyNotificationListener();   
        context.getManagementStrategy().getManagementAgent().getMBeanServer().addNotificationListener(on,
            listener, new NotificationFilter() {
                private static final long serialVersionUID = 1L;

                public boolean isNotificationEnabled(Notification notification) {
                    return true;
                }
            }, null);
        
        try {
            template.sendBody("direct:fail", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }
        
        assertEquals("Get a wrong number of events", 4, listener.getEventCounter());

        context.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("mock:result");

                from("direct:fail").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
    
    private class MyNotificationListener implements NotificationListener {
        
        private int eventCounter;

        @Override
        public void handleNotification(Notification notification, Object handback) {
            log.debug("Get the notification : " + notification);
            eventCounter++;            
        }
        
        public int getEventCounter() {
            return eventCounter;
        }
        
    }

}
