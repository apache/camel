/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.jmxconnect;


import junit.framework.TestCase;
import org.apache.activemq.broker.BrokerFactory;
import org.apache.activemq.broker.BrokerService;

import javax.management.*;
import javax.management.monitor.GaugeMonitor;
import javax.management.remote.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @version $Revision$
 */
public class JmxRemoteTest extends TestCase {
    private MBeanServer server;
    private BrokerService broker;
    private JMXConnectorServer connectorServer;
    private JMXConnector connector;
    private ObjectName serviceName;
    private SimpleService service;
    protected String serverServiceUrl = "service:jmx:camel:///activemq:foo";
    protected String clientServiceUrl = "service:jmx:camel:///activemq:foo";
    protected String brokerUrl = "broker:(tcp://localhost:61616)/localhost?persistent=false";

    protected void setUp() throws Exception {
        broker = BrokerFactory.createBroker(new URI(brokerUrl));
        broker.start();

        server = MBeanServerFactory.createMBeanServer();
        //register a service
        service = new SimpleService();

        serviceName = new ObjectName("examples", "mbean", "simple");
        server.registerMBean(service, serviceName);
        // start the connector server

        //START SNIPPET: serverJMX
        //The url to the JMS service
        JMXServiceURL serverURL = new JMXServiceURL(serverServiceUrl);
        Map serverEnv = new HashMap();
        serverEnv.put("jmx.remote.protocol.provider.pkgs", "org.apache.camel.jmxconnect.provider");
        connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(serverURL, serverEnv, server);
        connectorServer.start();
        //END SNIPPET: serverJMX

        //START SNIPPET: clientJMX
        //Now connect the client-side
        //The URL to the JMS service
        JMXServiceURL clientURL = new JMXServiceURL(clientServiceUrl);
        Map clientEnv = new HashMap();
        clientEnv.put("jmx.remote.protocol.provider.pkgs", "org.apache.camel.jmxconnect.provider");
        JMXConnector clientConnector = JMXConnectorFactory.connect(clientURL, clientEnv);
        // Connect a JSR 160 JMXConnector to the server side
        connector = JMXConnectorFactory.connect(clientURL, clientEnv);
        //now test the Connection
        MBeanServerConnection connection = connector.getMBeanServerConnection();
        //END SNIPPET: clientJMX

    }

    protected void tearDown() throws Exception {
        connector.close();
        connectorServer.stop();
        broker.stop();
    }


    public void testSimpleRemoteJmx() throws Exception {
        // Retrieve an MBeanServerConnection that represent the MBeanServer the remote
        // connector server is bound to
        MBeanServerConnection connection = connector.getMBeanServerConnection();
        ObjectName queryName = new ObjectName("*:*");
        java.util.Set names = connection.queryNames(queryName, null);
        for (Iterator iter = names.iterator(); iter.hasNext();) {
            ObjectName name = (ObjectName) iter.next();
            MBeanInfo beanInfo = connection.getMBeanInfo(name);
            System.out.println("bean info = " + beanInfo.getDescription());
            System.out.println("attrs = " + Arrays.asList(beanInfo.getAttributes()));
        }
        Object value = connection.getAttribute(serviceName, "SimpleValue");
        System.out.println("SimpleValue = " + value);
        Attribute attr = new Attribute("SimpleValue", new Integer(10));
        connection.setAttribute(serviceName, attr);
        value = connection.getAttribute(serviceName, "SimpleValue");
        assertEquals("SimpleValue", value, 10);
        System.out.println("now SimpleValue = " + value);
    }

    // TODO not implemented yet!
    // need server side push, the client needs to register with a replyToEndpoint
    public void DISABLED_testNotificationsJmx() throws Exception {

        // Now let's register a Monitor
        // We would like to know if we have peaks in activity, so we can use JMX's
        // GaugeMonitor
        GaugeMonitor monitorMBean = new GaugeMonitor();
        ObjectName monitorName = new ObjectName("examples", "monitor", "gauge");
        server.registerMBean(monitorMBean, monitorName);
        // Setup the monitor: we want to be notified if we have too many clients or too less
        monitorMBean.setThresholds(new Integer(8), new Integer(4));
        // Setup the monitor: we want to know if a threshold is exceeded
        monitorMBean.setNotifyHigh(true);
        monitorMBean.setNotifyLow(true);

        monitorMBean.setDifferenceMode(false);
        // Setup the monitor: link to the service MBean
        monitorMBean.addObservedObject(serviceName);
        monitorMBean.setObservedAttribute("SimpleCounter");
        // Setup the monitor: a short granularity period
        monitorMBean.setGranularityPeriod(50L);
        // Setup the monitor: register a listener
        MBeanServerConnection connection = connector.getMBeanServerConnection();
        final AtomicBoolean notificationSet = new AtomicBoolean(false);
        //Add a notification listener to the connection - to
        //test for notifications across camel
        connection.addNotificationListener(monitorName, new NotificationListener() {
            public void handleNotification(Notification notification, Object handback) {
                System.out.println("Notification = " + notification);
                synchronized (notificationSet) {
                    notificationSet.set(true);
                    notificationSet.notify();
                }
            }
        }, null, null);
        service.start();
        monitorMBean.start();
        synchronized (notificationSet) {
            if (!notificationSet.get()) {
                notificationSet.wait(5000);
            }
        }
        assertTrue(notificationSet.get());
    }
}