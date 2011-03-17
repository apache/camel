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
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * EventAdmin endpoint
 */
public class EventAdminEndpoint extends DefaultEndpoint {

    private final String topic;
    private boolean send;

    public EventAdminEndpoint(String uri, EventAdminComponent component, String topic) {
        super(uri, component);
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }

    public boolean isSend() {
        return send;
    }

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
        return new EventAdminConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }
}
