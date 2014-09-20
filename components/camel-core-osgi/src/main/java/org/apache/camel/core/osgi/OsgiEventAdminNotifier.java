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
package org.apache.camel.core.osgi;

import java.util.Dictionary;
import java.util.EventObject;
import java.util.Hashtable;

import org.apache.camel.support.EventNotifierSupport;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * This {@link org.apache.camel.spi.EventNotifier} is in charge of propagating events
 * to OSGi {@link EventAdmin} if present.
 */
public class OsgiEventAdminNotifier extends EventNotifierSupport {

    public static final String TYPE = "type";
    public static final String EVENT = "event";
    public static final String TIMESTAMP = "timestamp";
    public static final String BUNDLE = "bundle";
    public static final String BUNDLE_ID = "bundle.id";
    public static final String BUNDLE_SYMBOLICNAME = "bundle.symbolicName";
    public static final String BUNDLE_VERSION = "bundle.version";
    public static final String CAUSE = "cause";

    public static final String TOPIC_CAMEL_EVENTS = "org/apache/camel/";
    public static final String TOPIC_CAMEL_CONTEXT_EVENTS = TOPIC_CAMEL_EVENTS + "context/";
    public static final String TOPIC_CAMEL_EXCHANGE_EVENTS = TOPIC_CAMEL_EVENTS + "exchange/";
    public static final String TOPIC_CAMEL_SERVICE_EVENTS = TOPIC_CAMEL_EVENTS + "service/";
    public static final String TOPIC_CAMEL_ROUTE_EVENTS = TOPIC_CAMEL_EVENTS + "route/";

    private final BundleContext bundleContext;
    private final ServiceTracker<EventAdmin, EventAdmin> tracker;

    public OsgiEventAdminNotifier(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        this.tracker = new ServiceTracker<EventAdmin, EventAdmin>(bundleContext, EventAdmin.class.getName(), null);
        setIgnoreExchangeEvents(true);
    }

    public void notify(EventObject event) throws Exception {
        EventAdmin eventAdmin = tracker.getService();
        if (eventAdmin == null) {
            return;
        }

        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(TYPE, getType(event));
        props.put(EVENT, event);
        props.put(TIMESTAMP, System.currentTimeMillis());
        props.put(BUNDLE, bundleContext.getBundle());
        props.put(BUNDLE_SYMBOLICNAME, bundleContext.getBundle().getSymbolicName());
        props.put(BUNDLE_ID, bundleContext.getBundle().getBundleId());
        props.put(BUNDLE_VERSION, getBundleVersion(bundleContext.getBundle()));
        try {
            props.put(CAUSE, event.getClass().getMethod("getCause").invoke(event));
        } catch (Throwable t) {
            // ignore
        }
        eventAdmin.postEvent(new Event(getTopic(event), props));
    }

    public boolean isEnabled(EventObject event) {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        tracker.open();
    }

    @Override
    protected void doStop() throws Exception {
        tracker.close();
    }

    public static String toUpper(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    public static String getType(EventObject event) {
        String type = event.getClass().getSimpleName();
        if (type.endsWith("Event")) {
            type = type.substring(0, type.length() - "Event".length());
        }
        return type;
    }

    public static String getTopic(EventObject event) {
        String topic;
        String type = getType(event);
        if (type.startsWith("CamelContext")) {
            topic = TOPIC_CAMEL_CONTEXT_EVENTS;
            type = type.substring("CamelContext".length());
        } else if (type.startsWith("Exchange")) {
            topic = TOPIC_CAMEL_EXCHANGE_EVENTS;
            type = type.substring("Exchange".length());
        } else if (type.startsWith("Route")) {
            topic = TOPIC_CAMEL_ROUTE_EVENTS;
            type = type.substring("Route".length());
        } else if (type.startsWith("Service")) {
            topic = TOPIC_CAMEL_SERVICE_EVENTS;
            type = type.substring("Service".length());
        } else {
            topic = TOPIC_CAMEL_EVENTS + "unknown/";
        }
        topic += toUpper(type);
        return topic;
    }

    public static Version getBundleVersion(Bundle bundle) {
        Dictionary<?, ?> headers = bundle.getHeaders();
        String version = (String)headers.get(Constants.BUNDLE_VERSION);
        return (version != null) ? Version.parseVersion(version) : Version.emptyVersion;
    }

}
