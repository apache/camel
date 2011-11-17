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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.hazelcast.atomicnumber.HazelcastAtomicnumberEndpoint;
import org.apache.camel.component.hazelcast.instance.HazelcastInstanceEndpoint;
import org.apache.camel.component.hazelcast.list.HazelcastListEndpoint;
import org.apache.camel.component.hazelcast.map.HazelcastMapEndpoint;
import org.apache.camel.component.hazelcast.multimap.HazelcastMultimapEndpoint;
import org.apache.camel.component.hazelcast.queue.HazelcastQueueEndpoint;
import org.apache.camel.component.hazelcast.seda.HazelcastSedaConfiguration;
import org.apache.camel.component.hazelcast.seda.HazelcastSedaEndpoint;
import org.apache.camel.impl.DefaultComponent;

import static org.apache.camel.util.ObjectHelper.removeStartingCharacters;

public class HazelcastComponent extends DefaultComponent {

    public HazelcastComponent() {
        super();
    }

    public HazelcastComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        Endpoint endpoint = null;

        // check type of endpoint
        if (remaining.startsWith(HazelcastConstants.MAP_PREFIX)) {
            // remaining is the cache name
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.MAP_PREFIX.length()), '/');
            endpoint = new HazelcastMapEndpoint(uri, remaining, this);
        }

        if (remaining.startsWith(HazelcastConstants.MULTIMAP_PREFIX)) {
            // remaining is the cache name
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.MULTIMAP_PREFIX.length()), '/');
            endpoint = new HazelcastMultimapEndpoint(uri, remaining, this);
        }

        if (remaining.startsWith(HazelcastConstants.ATOMICNUMBER_PREFIX)) {
            // remaining is the name of the atomic value
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.INSTANCE_PREFIX.length()), '/');
            endpoint = new HazelcastAtomicnumberEndpoint(uri, this, remaining);
        }

        if (remaining.startsWith(HazelcastConstants.INSTANCE_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.INSTANCE_PREFIX.length()), '/');
            endpoint = new HazelcastInstanceEndpoint(uri, this);
        }

        if (remaining.startsWith(HazelcastConstants.QUEUE_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.QUEUE_PREFIX.length()), '/');
            endpoint = new HazelcastQueueEndpoint(uri, this, remaining);
        }

        if (remaining.startsWith(HazelcastConstants.SEDA_PREFIX)) {
            final HazelcastSedaConfiguration config = new HazelcastSedaConfiguration();
            setProperties(config, parameters);
            config.setQueueName(remaining.substring(remaining.indexOf(":") + 1, remaining.length()));

            endpoint = new HazelcastSedaEndpoint(uri, this, config);
        }

        if (remaining.startsWith(HazelcastConstants.LIST_PREFIX)) {
            // remaining is anything (name it foo ;)
            remaining = removeStartingCharacters(remaining.substring(HazelcastConstants.LIST_PREFIX.length()), '/');
            endpoint = new HazelcastListEndpoint(uri, this, remaining);
        }

        if (endpoint == null) {
            throw new IllegalArgumentException(String.format("Your URI does not provide a correct 'type' prefix. It should be anything like 'hazelcast:[%s|%s|%s|%s|%s|%s|%s]name' but is '%s'.",
                    HazelcastConstants.MAP_PREFIX, HazelcastConstants.MULTIMAP_PREFIX, HazelcastConstants.ATOMICNUMBER_PREFIX, HazelcastConstants.INSTANCE_PREFIX, HazelcastConstants.QUEUE_PREFIX,
                    HazelcastConstants.SEDA_PREFIX, HazelcastConstants.LIST_PREFIX, uri));
        }

        return endpoint;
    }

}
