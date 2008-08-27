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
import org.apache.camel.ProducerTemplate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @version $Revision$
 */
public class MBeanCamelServerConnectionImpl extends MBeanServerConnectionDelegate implements MBeanCamelServerConnection {
    private static final Log log = LogFactory.getLog(MBeanCamelServerConnectionImpl.class);
    private Map notificationListeners = new ConcurrentHashMap();
    private final ProducerTemplate template;

    public MBeanCamelServerConnectionImpl(MBeanServerConnection connection, ProducerTemplate template) {
        super(connection);
        this.template = template;
    }

    /**
     * Add a Notification listener
     *
     * @param listenerId
     * @param name
     * @param replyToEndpoint
     */
    public void addNotificationListener(String listenerId, ObjectName name, Endpoint replyToEndpoint) {
        try {
            ServerListenerInfo info = new ServerListenerInfo(listenerId, notificationListeners, template, replyToEndpoint);
            notificationListeners.put(listenerId, info);
            connection.addNotificationListener(name, info, null, null);
        } catch (Exception e) {
            log.error("Failed to addNotificationListener ", e);
        }

    }

    /**
     * Remove a Notification listener
     *
     * @param listenerId
     */
    public void removeNotificationListener(String listenerId) {
        ServerListenerInfo info = (ServerListenerInfo) notificationListeners.remove(listenerId);
        if (info != null) {
            info.close();
        }
    }

}