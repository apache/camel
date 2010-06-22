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

import org.apache.camel.spi.EventNotifier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A JMX based {@link EventNotifier} which broadcasts JMX {@link Notification}s.
 *
 * @version $Revision$
 */
public class JmxNotificationEventNotifier extends EventNotifierSupport implements JmxNotificationBroadcasterAware {
    private static final transient Log LOG = LogFactory.getLog(JmxNotificationEventNotifier.class);
    private final AtomicLong counter = new AtomicLong();
    private NotificationBroadcasterSupport notificationBroadcaster;
    
    public void setNotificationBroadcaster(NotificationBroadcasterSupport broadcaster) {
        notificationBroadcaster = broadcaster;
    }

    public void notify(EventObject event) throws Exception {
        if (notificationBroadcaster != null) {
            // use simple class name as the type
            String type = event.getClass().getSimpleName();
            Notification notification = new Notification(type, event, counter.getAndIncrement());

            if (LOG.isTraceEnabled()) {
                LOG.trace("Broadcasting JMX notification: " + notification);
            }
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
}
