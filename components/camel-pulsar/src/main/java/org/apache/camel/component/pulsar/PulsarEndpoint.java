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
package org.apache.camel.component.pulsar;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.pulsar.client.api.PulsarClient;

@UriEndpoint(scheme = "pulsar", firstVersion = "2.24.0", title = "Pulsar", syntax = "pulsar:persistence://tenant/namespace/topic", label = "messaging")
public class PulsarEndpoint extends DefaultEndpoint {

    private PulsarClient pulsarClient;
    private String uri;

    @UriPath(enums = "persistent,non-persistent")
    @Metadata(required = true)
    private String persistence;
    @UriPath
    @Metadata(required = true)
    private String tenant;
    @UriPath
    @Metadata(required = true)
    private String namespace;
    @UriPath
    @Metadata(required = true)
    private String topic;

    @UriParam(defaultValue = "true", label = "advanced")
    private boolean synchronous;

    @UriParam
    private PulsarConfiguration pulsarConfiguration;

    public PulsarEndpoint(String uri, PulsarComponent component) {
        super(uri, component);
        this.synchronous = true;
    }

    @Override
    public Producer createProducer() {
        return new PulsarProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        PulsarConsumer consumer = new PulsarConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public PulsarClient getPulsarClient() {
        return pulsarClient;
    }

    /**
     * To use a custom pulsar client
     */
    public void setPulsarClient(PulsarClient pulsarClient) {
        this.pulsarClient = pulsarClient;
    }

    public String getPersistence() {
        return persistence;
    }

    /**
     * Whether the topic is persistent or non-persistent
     */
    public void setPersistence(String persistence) {
        this.persistence = persistence;
    }

    public String getTenant() {
        return tenant;
    }

    /**
     * The tenant
     */
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * The namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * The topic
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * Returns whether synchronous processing should be strictly used.
     */
    @Override
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Sets whether synchronous processing should be strictly used, or Camel is
     * allowed to use asynchronous processing (if supported).
     */
    @Override
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public PulsarConfiguration getPulsarConfiguration() {
        return pulsarConfiguration;
    }

    public void setPulsarConfiguration(PulsarConfiguration pulsarConfiguration) {
        this.pulsarConfiguration = pulsarConfiguration;
    }

    public String getUri() {
        return persistence + "://" + tenant + "/" + namespace + "/" + topic;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(persistence, "persistence", this);
        ObjectHelper.notNull(tenant, "tenant", this);
        ObjectHelper.notNull(namespace, "namespace", this);
        ObjectHelper.notNull(topic, "topic", this);

        uri = persistence + "://" + tenant + "/" + namespace + "/" + topic;
    }

    @Override
    public PulsarComponent getComponent() {
        return (PulsarComponent)super.getComponent();
    }
}
