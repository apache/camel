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
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

public abstract class HazelcastDefaultEndpoint extends DefaultEndpoint {

    protected final String cacheName;
    protected HazelcastInstance hazelcastInstance;
    private int defaultOperation = -1;
    private final HazelcastComponentHelper helper = new HazelcastComponentHelper();

    public HazelcastDefaultEndpoint(HazelcastInstance hazelcastInstance, String endpointUri, Component component) {
        this(hazelcastInstance, endpointUri, component, null);
    }

    public HazelcastDefaultEndpoint(HazelcastInstance hazelcastInstance, String endpointUri, Component component, String cacheName) {
        super(endpointUri, component);
        this.cacheName = cacheName;
        this.hazelcastInstance = hazelcastInstance;
    }

    public abstract Consumer createConsumer(Processor processor) throws Exception;

    public abstract Producer createProducer() throws Exception;

    public boolean isSingleton() {
        return true;
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);
        defaultOperation = helper.extractOperationNumber(options.remove(HazelcastConstants.OPERATION_PARAM), -1);
    }

    public int getDefaultOperation() {
        return defaultOperation;
    }
}
