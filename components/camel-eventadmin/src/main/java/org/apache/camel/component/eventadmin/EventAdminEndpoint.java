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

import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The eventadmin component can be used in an OSGi environment to receive OSGi EventAdmin events and process them.
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "eventadmin", title = "OSGi EventAdmin", syntax = "eventadmin:topic", consumerClass = EventAdminConsumer.class, label = "eventbus")
public class EventAdminEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    @UriPath
    private final String topic;
    @UriParam
    private boolean send;

    public EventAdminEndpoint(String uri, EventAdminComponent component, String topic) {
        super(uri, component);
        this.topic = topic;
    }

    /**
     * Name of topic to listen or send to
     */
    public String getTopic() {
        return topic;
    }

    public boolean isSend() {
        return send;
    }

    /**
     * Whether to use 'send' or 'synchronous' deliver.
     * Default false (async delivery)
     */
    public void setSend(boolean send) {
        this.send = send;
    }

    public EventAdminComponent getComponent() {
        return (EventAdminComponent) super.getComponent();
    }

    public Producer createProducer() throws Exception {
        return new EventAdminProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        EventAdminConsumer answer = new EventAdminConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }
}
