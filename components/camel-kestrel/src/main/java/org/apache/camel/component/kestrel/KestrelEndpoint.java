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
package org.apache.camel.component.kestrel;

import net.spy.memcached.MemcachedClient;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The kestrel component allows messages to be sent to (or consumed from) Kestrel brokers.
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "kestrel", title = "Kestrel", syntax = "kestrel:addresses/queue", consumerClass = KestrelConsumer.class, label = "messaging")
public class KestrelEndpoint extends DefaultEndpoint {

    /**
     * The configuration of this endpoint
     */
    @UriParam
    private KestrelConfiguration configuration;

    /**
     * The queue we are polling
     */
    @UriPath @Metadata(required = "true")
    private String queue;

    /**
     * The kestrel component itself
     */
    private KestrelComponent component;

    public KestrelEndpoint(String endPointURI, KestrelComponent component, KestrelConfiguration configuration, String queue) {
        super(endPointURI, component);
        this.component = component;
        this.configuration = configuration;
        this.queue = queue;
    }

    public KestrelConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(KestrelConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public Producer createProducer() throws Exception {
        return new KestrelProducer(this, getMemcachedClient());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        KestrelConsumer answer = new KestrelConsumer(this, processor, getMemcachedClient());
        configureConsumer(answer);
        return answer;
    }

    /**
     * @return a client to kestrel using the memcached client as configured by this endpoint
     */
    private MemcachedClient getMemcachedClient() {
        return component.getMemcachedClient(configuration, queue);
    }

    public boolean isSingleton() {
        return true;
    }
}
