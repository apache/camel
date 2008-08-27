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

import javax.management.Notification;
import javax.management.NotificationListener;
import java.util.Map;

/**
 * @version $Revision$
 */
class ServerListenerInfo implements NotificationListener {
    private static final Log log = LogFactory.getLog(ServerListenerInfo.class);
    private final String id;
    private final Map holder;
    private final ProducerTemplate template;
    private final Endpoint replyToEndpoint;

    ServerListenerInfo(String id, Map holder, ProducerTemplate template, Endpoint replyToEndpoint) {
        this.id = id;
        this.holder = holder;
        this.template = template;
        this.replyToEndpoint = replyToEndpoint;
    }

    /**
     * NotificationListener implementation
     *
     * @param notification
     * @param handback
     */
    public void handleNotification(Notification notification, Object handback) {
        System.out.println("Should be sending notification: " + notification);
        if (replyToEndpoint == null) {
            log.warn("No replyToDestination for replies to be received so cannot send notification: " + notification);
        } else {
            template.sendBody(replyToEndpoint, notification);
        }
    }

    /**
     * close the info if the connection times out
     * <p/>
     * TODO we should auto-detect that id has timed out and then remove this subscription
     */
    public void close() {
        holder.remove(id);
    }

    /**
     * @return Returns the holder.
     */
    public Map getHolder() {
        return holder;
    }


    /**
     * @return Returns the id.
     */
    public String getId() {
        return id;
    }

}