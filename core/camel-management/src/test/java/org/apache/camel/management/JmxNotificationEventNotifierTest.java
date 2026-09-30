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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_EVENT_NOTIFIER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class JmxNotificationEventNotifierTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // START SNIPPET: e1
        // Set up the JmxNotificationEventNotifier
        JmxNotificationEventNotifier notifier = new JmxNotificationEventNotifier();
        notifier.setSource("MyCamel");
        notifier.setIgnoreCamelContextEvents(true);
        notifier.setIgnoreRouteEvents(true);
        notifier.setIgnoreServiceEvents(true);

        CamelContext context = new DefaultCamelContext(createCamelRegistry());
        context.getManagementStrategy().addEventNotifier(notifier);

        // END SNIPPET: e1
        return context;
    }

    @Test
    public void testExchangeDone() throws Exception {
        // START SNIPPET: e2
        // register the NotificationListener
        ObjectName on = getCamelObjectName(TYPE_EVENT_NOTIFIER, "JmxEventNotifier");
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

        assertEquals(8, listener.getEventCounter(), "Get a wrong number of events");

        // the notification types that were sent are advertised by the MBean
        Set<String> advertised = new HashSet<>();
        for (MBeanNotificationInfo info : context.getManagementStrategy().getManagementAgent().getMBeanServer()
                .getMBeanInfo(on).getNotifications()) {
            assertEquals(Notification.class.getName(), info.getName());
            advertised.addAll(Arrays.asList(info.getNotifTypes()));
        }
        assertFalse(listener.getTypes().isEmpty());
        for (String type : listener.getTypes()) {
            assertTrue(advertised.contains(type), "Notification type " + type + " should be advertised");
        }

        context.stop();
    }

    @Test
    public void testExchangeFailed() throws Exception {
        ObjectName on = getCamelObjectName(TYPE_EVENT_NOTIFIER, "JmxEventNotifier");

        MyNotificationListener listener = new MyNotificationListener();
        context.getManagementStrategy().getManagementAgent().getMBeanServer().addNotificationListener(on,
                listener, new NotificationFilter() {
                    private static final long serialVersionUID = 1L;

                    public boolean isNotificationEnabled(Notification notification) {
                        return true;
                    }
                }, null);

        Exception e = assertThrows(Exception.class, () -> template.sendBody("direct:fail", "Hello World"));
        assertIsInstanceOf(IllegalArgumentException.class, e.getCause());

        assertEquals(4, listener.getEventCounter(), "Get a wrong number of events");

        context.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("log:foo").to("mock:result");

                from("direct:fail").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }

    private class MyNotificationListener implements NotificationListener {

        private int eventCounter;
        private final Set<String> types = new HashSet<>();

        @Override
        public void handleNotification(Notification notification, Object handback) {
            log.debug("Get the notification : {}", notification);
            eventCounter++;
            types.add(notification.getType());
        }

        public Set<String> getTypes() {
            return types;
        }

        public int getEventCounter() {
            return eventCounter;
        }

    }

}
