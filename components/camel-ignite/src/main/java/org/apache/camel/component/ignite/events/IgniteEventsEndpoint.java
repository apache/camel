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
package org.apache.camel.component.ignite.events;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ignite.AbstractIgniteEndpoint;
import org.apache.camel.component.ignite.ClusterGroupExpression;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteEvents;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.events.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.ignite.IgniteConstants.SCHEME_EVENTS;

/**
 * <a href="https://apacheignite.readme.io/docs/events">Receive events</a> from an Ignite cluster by creating a local
 * event listener.
 *
 * This endpoint only supports consumers. The Exchanges created by this consumer put the received Event object into the
 * body of the IN message.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = SCHEME_EVENTS, title = "Ignite Events", syntax = "ignite-events:endpointId",
             category = { Category.MESSAGING, Category.CACHE, Category.CLUSTERING },
             consumerOnly = true)
public class IgniteEventsEndpoint extends AbstractIgniteEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(IgniteEventsEndpoint.class);

    @UriPath
    private String endpointId;

    @UriParam(label = "consumer", defaultValue = "EVTS_ALL")
    private String events = "EVTS_ALL";

    @UriParam(label = "consumer")
    private ClusterGroupExpression clusterGroupExpression;

    public IgniteEventsEndpoint(String uri, String remaining, Map<String, Object> parameters,
                                IgniteEventsComponent igniteComponent) {
        super(uri, igniteComponent);
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("The Ignite Events endpoint does not support producers.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // Initialize the Consumer.
        IgniteEvents igniteEvents = createIgniteEvents();
        IgniteEventsConsumer consumer = new IgniteEventsConsumer(this, processor, igniteEvents);
        configureConsumer(consumer);

        LOG.info("Created Ignite Events consumer for event types: {}.", igniteEvents);

        return consumer;
    }

    private IgniteEvents createIgniteEvents() {
        Ignite ignite = ignite();
        IgniteEvents igniteEvents;
        if (clusterGroupExpression == null) {
            LOG.info("Ignite Events endpoint for event types {} using no Cluster Group.", this.events);
            igniteEvents = ignite.events();
        } else {
            ClusterGroup group = clusterGroupExpression.getClusterGroup(ignite);
            LOG.info("Ignite Events endpoint for event types {} using Cluster Group: {}.", this.events, group);
            igniteEvents = ignite.events(group);
        }
        return igniteEvents;
    }

    /**
     * Gets the endpoint ID (not used).
     */
    public String getEndpointId() {
        return endpointId;
    }

    /**
     * The endpoint ID (not used).
     */
    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }

    /**
     * Gets the event types to subscribe to.
     */
    public String getEvents() {
        return events;
    }

    /**
     * The event types to subscribe to as a comma-separated string of event constants as defined in {@link EventType}.
     * For example: EVT_CACHE_ENTRY_CREATED,EVT_CACHE_OBJECT_REMOVED,EVT_IGFS_DIR_CREATED.
     */
    public void setEvents(String events) {
        this.events = events;
    }

    public List<Integer> getEventsAsIds() {
        List<Integer> answer = new ArrayList<>();

        if (events.equals("EVTS_ALL")) {
            for (Integer eventType : EventType.EVTS_ALL) {
                answer.add(eventType);
            }
        } else {
            Set<String> requestedEvents = new HashSet<>(Arrays.asList(events.toUpperCase().split(",")));
            Field[] fields = EventType.class.getDeclaredFields();
            for (Field field : fields) {
                if (!requestedEvents.contains(field.getName())) {
                    continue;
                }
                try {
                    answer.add(field.getInt(null));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Problem while resolving event type. See stacktrace.", e);
                }
            }
        }

        return answer;
    }

    /**
     * Gets the cluster group expression.
     */
    public ClusterGroupExpression getClusterGroupExpression() {
        return clusterGroupExpression;
    }

    /**
     * The cluster group expression.
     */
    public void setClusterGroupExpression(ClusterGroupExpression clusterGroupExpression) {
        this.clusterGroupExpression = clusterGroupExpression;
    }

}
