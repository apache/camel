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
package org.apache.camel.component.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The hazelcast component allows you to work with the Hazelcast distributed data grid / cache.
 */
public abstract class HazelcastDefaultEndpoint extends DefaultEndpoint {

    protected HazelcastCommand command;
    @UriPath @Metadata(required = true)
    protected String cacheName;
    @UriParam
    protected HazelcastInstance hazelcastInstance;
    @UriParam
    protected String hazelcastInstanceName;
    @UriParam
    private HazelcastOperation defaultOperation;

    public HazelcastDefaultEndpoint(HazelcastInstance hazelcastInstance, String endpointUri, Component component) {
        this(hazelcastInstance, endpointUri, component, null);
    }

    public HazelcastDefaultEndpoint(HazelcastInstance hazelcastInstance, String endpointUri, Component component, String cacheName) {
        super(endpointUri, component);
        this.cacheName = cacheName;
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public abstract Consumer createConsumer(Processor processor) throws Exception;

    @Override
    public abstract Producer createProducer() throws Exception;

    public HazelcastCommand getCommand() {
        return command;
    }

    /**
     * What operation to perform.
     */
    public void setCommand(HazelcastCommand command) {
        this.command = command;
    }

    public String getCacheName() {
        return cacheName;
    }

    /**
     * The name of the cache
     */
    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    /**
     * The hazelcast instance reference which can be used for hazelcast endpoint.
     */
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    public String getHazelcastInstanceName() {
        return hazelcastInstanceName;
    }

    /**
     * The hazelcast instance reference name which can be used for hazelcast endpoint.
     * If you don't specify the instance reference, camel use the default hazelcast instance from the camel-hazelcast instance.
     */
    public void setHazelcastInstanceName(String hazelcastInstanceName) {
        this.hazelcastInstanceName = hazelcastInstanceName;
    }

    /**
     * To specify a default operation to use, if no operation header has been provided.
     */
    public void setDefaultOperation(HazelcastOperation defaultOperation) {
        this.defaultOperation = defaultOperation;
    }

    public HazelcastOperation getDefaultOperation() {
        return defaultOperation;
    }

}
