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

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.hazelcast.atomicnumber.HazelcastAtomicnumberEndpoint;
import org.apache.camel.component.hazelcast.instance.HazelcastInstanceEndpoint;
import org.apache.camel.component.hazelcast.list.HazelcastListEndpoint;
import org.apache.camel.component.hazelcast.map.HazelcastMapEndpoint;
import org.apache.camel.component.hazelcast.multimap.HazelcastMultimapEndpoint;
import org.apache.camel.component.hazelcast.queue.HazelcastQueueEndpoint;
import org.apache.camel.component.hazelcast.replicatedmap.HazelcastReplicatedmapEndpoint;
import org.apache.camel.component.hazelcast.ringbuffer.HazelcastRingbufferEndpoint;
import org.apache.camel.component.hazelcast.seda.HazelcastSedaConfiguration;
import org.apache.camel.component.hazelcast.seda.HazelcastSedaEndpoint;
import org.apache.camel.component.hazelcast.set.HazelcastSetEndpoint;
import org.apache.camel.component.hazelcast.topic.HazelcastTopicEndpoint;
import org.apache.camel.impl.UriEndpointComponent;

import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_INSTANCE_NAME_PARAM;
import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_INSTANCE_PARAM;
import static org.apache.camel.util.ObjectHelper.removeStartingCharacters;

public class HazelcastComponent extends UriEndpointComponent {

    private final HazelcastComponentHelper helper = new HazelcastComponentHelper();

    private HazelcastInstance hazelcastInstance;
    private transient boolean createOwnInstance;

    public HazelcastComponent() {
        super(HazelcastDefaultEndpoint.class);
    }

    public HazelcastComponent(final CamelContext context) {
        super(context, HazelcastDefaultEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        // use the given hazelcast Instance or create one if not given
        HazelcastInstance hzInstance = getOrCreateHzInstance(parameters);

        int defaultOperation = -1;
        Object operation = getAndRemoveOrResolveReferenceParameter(parameters, HazelcastConstants.OPERATION_PARAM, Object.class);
        if (operation == null) {
            operation = getAndRemoveOrResolveReferenceParameter(parameters, "defaultOperation", Object.class);
        }
        if (operation != null) {
            defaultOperation = helper.extractOperationNumber(operation, -1);
        }
       
        HazelcastDefaultEndpoint endpoint = null;

        // check type of endpoint
        if (remaining.startsWith(HazelcastConstants.MAP_PREFIX)) {
            // remaining is the cache name
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.MAP_PREFIX.length()), '/');
            endpoint = new HazelcastMapEndpoint(hzInstance, uri, remaining, this);
            endpoint.setCommand(HazelcastCommand.map);
        }

        if (remaining.startsWith(HazelcastConstants.MULTIMAP_PREFIX)) {
            // remaining is the cache name
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.MULTIMAP_PREFIX.length()), '/');
            endpoint = new HazelcastMultimapEndpoint(hzInstance, uri, remaining, this);
            endpoint.setCommand(HazelcastCommand.multimap);
        }

        if (remaining.startsWith(HazelcastConstants.ATOMICNUMBER_PREFIX)) {
            // remaining is the name of the atomic value
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.ATOMICNUMBER_PREFIX.length()), '/');
            endpoint = new HazelcastAtomicnumberEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.atomicvalue);
        }

        if (remaining.startsWith(HazelcastConstants.INSTANCE_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.INSTANCE_PREFIX.length()), '/');
            endpoint = new HazelcastInstanceEndpoint(hzInstance, uri, this);
            endpoint.setCommand(HazelcastCommand.instance);
        }

        if (remaining.startsWith(HazelcastConstants.QUEUE_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.QUEUE_PREFIX.length()), '/');
            endpoint = new HazelcastQueueEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.queue);
        }

        if (remaining.startsWith(HazelcastConstants.TOPIC_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.TOPIC_PREFIX.length()), '/');
            endpoint = new HazelcastTopicEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.topic);
        }

        if (remaining.startsWith(HazelcastConstants.SEDA_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.SEDA_PREFIX.length()), '/');
            final HazelcastSedaConfiguration config = new HazelcastSedaConfiguration();
            setProperties(config, parameters);
            config.setQueueName(remaining);
            endpoint = new HazelcastSedaEndpoint(hzInstance, uri, this, config);
            endpoint.setCacheName(remaining);
            endpoint.setCommand(HazelcastCommand.seda);
        }

        if (remaining.startsWith(HazelcastConstants.LIST_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.LIST_PREFIX.length()), '/');
            endpoint = new HazelcastListEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.list);
        }

        if (remaining.startsWith(HazelcastConstants.REPLICATEDMAP_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.REPLICATEDMAP_PREFIX.length()), '/');
            endpoint = new HazelcastReplicatedmapEndpoint(hzInstance, uri, remaining, this);
            endpoint.setCommand(HazelcastCommand.replicatedmap);
        } 
        
        if (remaining.startsWith(HazelcastConstants.SET_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.SET_PREFIX.length()), '/');
            endpoint = new HazelcastSetEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.set);
        } 
        
        
        if (remaining.startsWith(HazelcastConstants.RINGBUFFER_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.RINGBUFFER_PREFIX.length()), '/');
            endpoint = new HazelcastRingbufferEndpoint(hzInstance, uri, this, remaining);
            endpoint.setCommand(HazelcastCommand.ringbuffer);
        } 
        
        if (endpoint == null) {
            throw new IllegalArgumentException(String.format("Your URI does not provide a correct 'type' prefix. It should be anything like " 
                    + "'hazelcast:[%s|%s|%s|%s|%s|%s|%s|%s|%s|%s]name' but is '%s'.",
                    HazelcastConstants.MAP_PREFIX, HazelcastConstants.MULTIMAP_PREFIX, HazelcastConstants.ATOMICNUMBER_PREFIX, HazelcastConstants.INSTANCE_PREFIX, HazelcastConstants.QUEUE_PREFIX,
                    HazelcastConstants.SEDA_PREFIX, HazelcastConstants.LIST_PREFIX, HazelcastConstants.REPLICATEDMAP_PREFIX, HazelcastConstants.SET_PREFIX, HazelcastConstants.RINGBUFFER_PREFIX, uri));
        }

        if (defaultOperation != -1) {
            endpoint.setDefaultOperation(defaultOperation);
        }

        return endpoint;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        if (createOwnInstance && hazelcastInstance != null) {
            hazelcastInstance.getLifecycleService().shutdown();
        }
        super.doStop();
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    /**
     * The hazelcast instance reference which can be used for hazelcast endpoint.
     * If you don't specify the instance reference, camel use the default hazelcast instance from the camel-hazelcast instance.
     */
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    private HazelcastInstance createOwnInstance() {
        Config config = new XmlConfigBuilder().build();
        // Disable the version check
        config.getProperties().setProperty("hazelcast.version.check.enabled", "false");
        return Hazelcast.newHazelcastInstance(config);
    }

    private HazelcastInstance getOrCreateHzInstance(Map<String, Object> parameters) {

        // Query param named 'hazelcastInstance' (if exists) overrides the instance that was set
        HazelcastInstance hzInstance = resolveAndRemoveReferenceParameter(parameters, HAZELCAST_INSTANCE_PARAM, HazelcastInstance.class);

        // check if an already created instance is given then just get instance by its name.
        if (hzInstance == null && parameters.get(HAZELCAST_INSTANCE_NAME_PARAM) != null) {
            hzInstance = Hazelcast.getHazelcastInstanceByName((String) parameters.get(HAZELCAST_INSTANCE_NAME_PARAM));
        }

        // Now create onw instance component
        if (hzInstance == null) {
            if (hazelcastInstance == null) {
                createOwnInstance = true;
                hazelcastInstance = createOwnInstance();
            }
            hzInstance = hazelcastInstance;
        }
        return hzInstance;
    }
}
