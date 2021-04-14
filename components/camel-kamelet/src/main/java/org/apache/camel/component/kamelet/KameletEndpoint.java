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
package org.apache.camel.component.kamelet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

@UriEndpoint(firstVersion = "3.8.0", scheme = "kamelet", syntax = "kamelet:templateId/routeId", title = "Kamelet",
             lenientProperties = true, category = Category.CORE)
public class KameletEndpoint extends DefaultEndpoint {
    private final String key;
    private final Map<String, Object> kameletProperties;

    @Metadata(required = true)
    @UriPath(description = "The Route Template ID")
    private final String templateId;
    @Metadata
    @UriPath(description = "The Route ID", defaultValueNote = "The ID will be auto-generated if not provided")
    private final String routeId;

    @UriParam(label = "producer", defaultValue = "true")
    private boolean block = true;
    @UriParam(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean failIfNoConsumers = true;

    public KameletEndpoint(
                           String uri,
                           KameletComponent component,
                           String templateId,
                           String routeId) {

        super(uri, component);

        ObjectHelper.notNull(templateId, "template id");
        ObjectHelper.notNull(routeId, "route id");

        this.templateId = templateId;
        this.routeId = routeId;
        this.key = templateId + "/" + routeId;
        this.kameletProperties = new HashMap<>();
    }

    public boolean isBlock() {
        return block;
    }

    /**
     * If sending a message to a direct endpoint which has no active consumer, then we can tell the producer to block
     * and wait for the consumer to become active.
     */
    public void setBlock(boolean block) {
        this.block = block;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout value to use if block is enabled.
     *
     * @param timeout the timeout value
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isFailIfNoConsumers() {
        return failIfNoConsumers;
    }

    /**
     * Whether the producer should fail by throwing an exception, when sending to a kamelet endpoint with no active
     * consumers.
     */
    public void setFailIfNoConsumers(boolean failIfNoConsumers) {
        this.failIfNoConsumers = failIfNoConsumers;
    }

    @Override
    public KameletComponent getComponent() {
        return (KameletComponent) super.getComponent();
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getRouteId() {
        return routeId;
    }

    public Map<String, Object> getKameletProperties() {
        return Collections.unmodifiableMap(kameletProperties);
    }

    /**
     * Custom properties for kamelet
     */
    public void setKameletProperties(Map<String, Object> kameletProperties) {
        if (kameletProperties != null) {
            this.kameletProperties.clear();
            this.kameletProperties.putAll(kameletProperties);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KameletProducer(this, key);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new KameletConsumer(this, processor, key);
        configureConsumer(answer);
        return answer;
    }

}
