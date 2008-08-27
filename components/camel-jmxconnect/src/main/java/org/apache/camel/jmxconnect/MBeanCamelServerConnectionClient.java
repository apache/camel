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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.UuidGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.*;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @version $Revision$
 */
public class MBeanCamelServerConnectionClient extends MBeanServerConnectionDelegate implements Processor {
    private static final Log log = LogFactory.getLog(MBeanCamelServerConnectionClient.class);
    private MBeanCamelServerConnection serverConnection;
    private Endpoint replyToEndpoint;
    private List listeners = new CopyOnWriteArrayList();
    private UuidGenerator idGenerator = new UuidGenerator();
    private NotificationBroadcasterSupport localNotifier = new NotificationBroadcasterSupport();

    public MBeanCamelServerConnectionClient(MBeanCamelServerConnection serverConnection) {
        super(serverConnection);
        this.serverConnection = serverConnection;
    }

    /**
     * Add a notification listener
     */
    public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter,
                                        Object handback) {
        String id = generateId();
        ListenerInfo info = new ListenerInfo(id, listener, filter, handback);
        listeners.add(info);
        localNotifier.addNotificationListener(listener, filter, handback);

        // TODO need to create an endpoint for replies!!!
        if (replyToEndpoint == null) {
            log.error("no replyToDestination for replies to be received!");
        }
        serverConnection.addNotificationListener(id, name, replyToEndpoint);
    }

    public String generateId() {
        return idGenerator.generateId();
    }

    /**
     * Remove a Notification Listener
     */
    public void removeNotificationListener(ObjectName name, NotificationListener listener)
            throws ListenerNotFoundException {
        for (Iterator i = listeners.iterator(); i.hasNext();) {
            ListenerInfo li = (ListenerInfo) i.next();
            if (li.getListener() == listener) {
                listeners.remove(i);
                serverConnection.removeNotificationListener(li.getId());
                break;
            }
        }
        localNotifier.removeNotificationListener(listener);
    }

    /**
     * Remove a Notification Listener
     */
    public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter,
                                           Object handback) throws ListenerNotFoundException {
        for (Iterator i = listeners.iterator(); i.hasNext();) {
            ListenerInfo li = (ListenerInfo) i.next();
            if (li.getListener() == listener && li.getFilter() == filter && li.getHandback() == handback) {
                listeners.remove(i);
                serverConnection.removeNotificationListener(li.getId());
            }
        }
        localNotifier.removeNotificationListener(listener, filter, handback);
    }


    public void process(Exchange exchange) throws Exception {
        Notification notification = exchange.getIn().getBody(Notification.class);
        if (notification != null) {
            localNotifier.sendNotification(notification);
        } else {
            log.warn("Received message which is not a Notification: " + exchange);
        }
    }
}