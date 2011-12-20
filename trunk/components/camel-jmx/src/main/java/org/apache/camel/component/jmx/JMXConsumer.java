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
package org.apache.camel.component.jmx;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

/**
 * Consumer will add itself as a NotificationListener on the object
 * specified by the objectName param.
 */
public class JMXConsumer extends DefaultConsumer implements NotificationListener {

    /**
     * connection to the mbean server (local or remote)
     */
    private MBeanServerConnection mServerConnection;

    /**
     * used to format Notification objects as xml
     */
    private NotificationXmlFormatter mFormatter;

    public JMXConsumer(JMXEndpoint aEndpoint, Processor aProcessor) {
        super(aEndpoint, aProcessor);
        mFormatter = new NotificationXmlFormatter();
    }

    /**
     * Initializes the mbean server connection and starts listening for
     * Notification events from the object.
     */
    @Override
    protected void doStart() throws Exception {
        super.doStart();

        JMXEndpoint ep = (JMXEndpoint) getEndpoint();

        // connect to the mbean server
        if (ep.isPlatformServer()) {
            setServerConnection(ManagementFactory.getPlatformMBeanServer());
        } else {
            JMXServiceURL url = new JMXServiceURL(ep.getServerURL());
            String[] creds = {ep.getUser(), ep.getPassword()};
            Map<String, String[]> map = Collections.singletonMap(JMXConnector.CREDENTIALS, creds);
            JMXConnector connector = JMXConnectorFactory.connect(url, map);
            setServerConnection(connector.getMBeanServerConnection());
        }
        // subscribe
        addNotificationListener();
    }

    /**
     * Adds a notification listener to the target bean.
     * @throws Exception
     */
    protected void addNotificationListener() throws Exception {
        JMXEndpoint ep = (JMXEndpoint) getEndpoint();
        NotificationFilter nf = ep.getNotificationFilter();

        ObjectName objectName = ep.getJMXObjectName();

        getServerConnection().addNotificationListener(objectName, this, nf, ep.getHandback());
    }

    /**
     * Removes the notification listener
     */
    @Override
    protected void doStop() throws Exception {
        super.doStop();
        removeNotificationListener();
    }

    /**
     * Removes the consumer as a listener from the bean. 
     */
    protected void removeNotificationListener() throws Exception {
        JMXEndpoint ep = (JMXEndpoint) getEndpoint();
        getServerConnection().removeNotificationListener(ep.getJMXObjectName(), this);
    }

    protected MBeanServerConnection getServerConnection() {
        return mServerConnection;
    }

    protected void setServerConnection(MBeanServerConnection aServerConnection) {
        mServerConnection = aServerConnection;
    }

    /**
     * Processes the Notification received. The handback will be set as
     * the header "jmx.handback" while the Notification will be set as
     * the body.
     * <p/>
     * If the format is set to "xml" then the Notification will be converted
     * to XML first using {@link NotificationXmlFormatter}
     *
     * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
     */
    public void handleNotification(Notification aNotification, Object aHandback) {
        JMXEndpoint ep = (JMXEndpoint) getEndpoint();
        Exchange exchange = getEndpoint().createExchange(ExchangePattern.InOnly);
        Message message = exchange.getIn();
        message.setHeader("jmx.handback", aHandback);
        try {
            if (ep.isXML()) {
                message.setBody(getFormatter().format(aNotification));
            } else {
                message.setBody(aNotification);
            }
            getProcessor().process(exchange);
        } catch (NotificationFormatException e) {
            getExceptionHandler().handleException("Failed to marshal notification", e);
        } catch (Exception e) {
            getExceptionHandler().handleException("Failed to process notification", e);
        }
    }

    protected NotificationXmlFormatter getFormatter() {
        return mFormatter;
    }
}
