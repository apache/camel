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
package org.apache.camel.management;

import java.util.EventObject;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;

import org.apache.camel.api.management.JmxNotificationBroadcasterAware;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.support.EventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JMX based {@link EventNotifier} which broadcasts JMX {@link Notification}s.
 *
 * @version 
 */
public class JmxNotificationEventNotifier extends EventNotifierSupport implements JmxNotificationBroadcasterAware {
    private static final Logger LOG = LoggerFactory.getLogger(JmxNotificationEventNotifier.class);
    private final AtomicLong counter = new AtomicLong();
    private NotificationBroadcasterSupport notificationBroadcaster;
    private String source = "Camel";

    public void setNotificationBroadcaster(NotificationBroadcasterSupport broadcaster) {
        notificationBroadcaster = broadcaster;
    }

    public void notify(EventObject event) throws Exception {
        if (notificationBroadcaster != null) {
            // its recommended to send light weight events and we don't want to have the entire Exchange/CamelContext etc
            // serialized as these are the typical source of the EventObject. So we use our own source which is just
            // a human readable name, which can be configured.
            String type = event.getClass().getSimpleName();
            String message = event.toString();
            Notification notification = new Notification(type, source, counter.getAndIncrement(), message);

            LOG.trace("Broadcasting JMX notification: {}", notification);
            notificationBroadcaster.sendNotification(notification);
        }
    }

    public boolean isEnabled(EventObject event) {
        return true;
    }

    protected void doStart() throws Exception {
        counter.set(0);
    }

    protected void doStop() throws Exception {
        // noop
    }

    public String getSource() {
        return source;
    }

    /**
     * Sets the source to be used when broadcasting events.
     * The source is just a readable identifier which helps the receiver see where the event is coming from.
     * You can assign a value such a server or application name etc.
     * <p/>
     * By default <tt>Camel</tt> will be used as source.
     *
     * @param source  the source
     */
    public void setSource(String source) {
        this.source = source;
    }
}
