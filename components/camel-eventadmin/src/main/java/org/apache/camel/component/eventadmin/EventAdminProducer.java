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
package org.apache.camel.component.eventadmin;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.CamelContextHelper;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * EventAdmin producer
 */
public class EventAdminProducer extends DefaultProducer {

    private final EventAdminEndpoint endpoint;
    private ServiceTracker<Object, Object> tracker;

    public EventAdminProducer(EventAdminEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.tracker = new ServiceTracker<Object, Object>(endpoint.getComponent().getBundleContext(), EventAdmin.class.getName(), null);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.tracker.open();
    }

    @Override
    protected void doStop() throws Exception {
        this.tracker.close();
        super.doStop();
    }

    public void process(Exchange exchange) throws Exception {
        EventAdmin admin = (EventAdmin) this.tracker.getService();
        if (admin != null) {
            Event event = getEvent(exchange);
            if (endpoint.isSend()) {
                admin.sendEvent(event);
            } else {
                admin.postEvent(event);
            }
        } else {
            throw new CamelExchangeException("EventAdmin service not present", exchange);
        }
    }

    protected String getTopic(Exchange exchange) {
        Message in = exchange.getIn();
        String topic = in.getHeader(EventAdminConstants.EVENTADMIN_TOPIC, String.class);
        if (topic != null) {
            in.removeHeader(EventAdminConstants.EVENTADMIN_TOPIC);
        }
        if (topic == null) {
            topic = endpoint.getTopic();
        }
        return topic;
    }

    protected Event getEvent(Exchange exchange) {
        Message in = exchange.getIn();
        CamelContext context = endpoint.getCamelContext();
        Event event = context.getTypeConverter().convertTo(Event.class, exchange, in.getBody());
        if (event == null) {
            String topic = getTopic(exchange);
            Dictionary<String, ?> props = getProperties(exchange);
            event = new Event(topic, props);
        }
        return event;
    }

    protected Dictionary<String, ?> getProperties(Exchange exchange) {
        Message in = exchange.getIn();
        CamelContext context = endpoint.getCamelContext();
        Map<?, ?> map = context.getTypeConverter().convertTo(Map.class, exchange, in.getBody());
        Dictionary<String, Object> dict = new Hashtable<String, Object>();
        for (Entry<?, ?> entry : map.entrySet()) {
            String keyString = CamelContextHelper.convertTo(context, String.class, entry.getKey());
            if (keyString != null) {
                Object val = entry.getValue();
                // TODO: convert to acceptable value
                dict.put(keyString, val);
            }
        }
        return dict;
    }

}
