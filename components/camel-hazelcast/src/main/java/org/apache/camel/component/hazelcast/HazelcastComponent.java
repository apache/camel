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
package org.apache.camel.component.hazelcast;

import java.util.Map;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.component.hazelcast.atomicnumber.HazelcastAtomicnumberEndpoint;
import org.apache.camel.component.hazelcast.instance.HazelcastInstanceEndpoint;
import org.apache.camel.component.hazelcast.list.HazelcastListEndpoint;
import org.apache.camel.component.hazelcast.map.HazelcastMapEndpoint;
import org.apache.camel.component.hazelcast.multimap.HazelcastMultimapEndpoint;
import org.apache.camel.component.hazelcast.queue.HazelcastQueueConfiguration;
import org.apache.camel.component.hazelcast.queue.HazelcastQueueEndpoint;
import org.apache.camel.component.hazelcast.replicatedmap.HazelcastReplicatedmapEndpoint;
import org.apache.camel.component.hazelcast.ringbuffer.HazelcastRingbufferEndpoint;
import org.apache.camel.component.hazelcast.seda.HazelcastSedaConfiguration;
import org.apache.camel.component.hazelcast.seda.HazelcastSedaEndpoint;
import org.apache.camel.component.hazelcast.set.HazelcastSetEndpoint;
import org.apache.camel.component.hazelcast.topic.HazelcastTopicConfiguration;
import org.apache.camel.component.hazelcast.topic.HazelcastTopicEndpoint;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated
 * 
 */
@Deprecated
public class HazelcastComponent extends HazelcastDefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastComponent.class);

    public HazelcastComponent() {
        super();
    }

    public HazelcastComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected HazelcastDefaultEndpoint doCreateEndpoint(String uri, String remaining, Map<String, Object> parameters, HazelcastInstance hzInstance) throws Exception {

        HazelcastDefaultEndpoint endpoint = null;

        LOG.warn("The scheme syntax 'hazelcast:{}' has been deprecated. Use 'hazelcast-{}' instead.", remaining, remaining);

        // check type of endpoint
        if (remaining.startsWith(HazelcastConstants.MAP_PREFIX)) {
            // remaining is the cache name
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.MAP_PREFIX.length()), '/');
            endpoint = new HazelcastMapEndpoint(hzInstance, uri, remaining, this);
            endpoint.setCommand(HazelcastCommand.map);
        }

        if (remaining.startsWith(HazelcastConstants.MULTIMAP_PREFIX)) {
            // remaining is the cache name
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.MULTIMAP_PREFIX.length()), '/');
            endpoint = new HazelcastMultimapEndpoint(hzInstance, uri, remaining, this);
            endpoint.setCommand(HazelcastCommand.multimap);
        }

        if (remaining.startsWith(HazelcastConstants.ATOMICNUMBER_PREFIX)) {
            // remaining is the name of the atomic value
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.ATOMICNUMBER_PREFIX.length()), '/');
            endpoint = new HazelcastAtomicnumberEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.atomicvalue);
        }

        if (remaining.startsWith(HazelcastConstants.INSTANCE_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.INSTANCE_PREFIX.length()), '/');
            endpoint = new HazelcastInstanceEndpoint(hzInstance, uri, this);
            endpoint.setCommand(HazelcastCommand.instance);
        }

        if (remaining.startsWith(HazelcastConstants.QUEUE_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.QUEUE_PREFIX.length()), '/');
            final HazelcastQueueConfiguration config = new HazelcastQueueConfiguration();
            setProperties(config, parameters);
            endpoint = new HazelcastQueueEndpoint(hzInstance, uri, this, remaining, config);
            endpoint.setCommand(HazelcastCommand.queue);
        }

        if (remaining.startsWith(HazelcastConstants.TOPIC_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.TOPIC_PREFIX.length()), '/');
            final HazelcastTopicConfiguration config = new HazelcastTopicConfiguration();
            setProperties(config, parameters);
            endpoint = new HazelcastTopicEndpoint(hzInstance, uri, this, remaining, config);
            endpoint.setCommand(HazelcastCommand.topic);
        }

        if (remaining.startsWith(HazelcastConstants.SEDA_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.SEDA_PREFIX.length()), '/');
            final HazelcastSedaConfiguration config = new HazelcastSedaConfiguration();
            setProperties(config, parameters);
            config.setQueueName(remaining);
            endpoint = new HazelcastSedaEndpoint(hzInstance, uri, this, config);
            endpoint.setCacheName(remaining);
            endpoint.setCommand(HazelcastCommand.seda);
        }

        if (remaining.startsWith(HazelcastConstants.LIST_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.LIST_PREFIX.length()), '/');
            endpoint = new HazelcastListEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.list);
        }

        if (remaining.startsWith(HazelcastConstants.REPLICATEDMAP_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.REPLICATEDMAP_PREFIX.length()), '/');
            endpoint = new HazelcastReplicatedmapEndpoint(hzInstance, uri, remaining, this);
            endpoint.setCommand(HazelcastCommand.replicatedmap);
        } 
        
        if (remaining.startsWith(HazelcastConstants.SET_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.SET_PREFIX.length()), '/');
            endpoint = new HazelcastSetEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.set);
        } 
        
        
        if (remaining.startsWith(HazelcastConstants.RINGBUFFER_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = StringHelper.removeStartingCharacters(remaining.substring(HazelcastConstants.RINGBUFFER_PREFIX.length()), '/');
            endpoint = new HazelcastRingbufferEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.ringbuffer);
        } 
        
        if (endpoint == null) {
            throw new IllegalArgumentException(String.format("Your URI does not provide a correct 'type' prefix. It should be anything like " 
                    + "'hazelcast:[%s|%s|%s|%s|%s|%s|%s|%s|%s|%s]name' but is '%s'.",
                    HazelcastConstants.MAP_PREFIX, HazelcastConstants.MULTIMAP_PREFIX, HazelcastConstants.ATOMICNUMBER_PREFIX, HazelcastConstants.INSTANCE_PREFIX, HazelcastConstants.QUEUE_PREFIX,
                    HazelcastConstants.SEDA_PREFIX, HazelcastConstants.LIST_PREFIX, HazelcastConstants.REPLICATEDMAP_PREFIX, HazelcastConstants.SET_PREFIX, HazelcastConstants.RINGBUFFER_PREFIX, uri));
        }
        return endpoint;
    }

}
