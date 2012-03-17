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
package org.apache.camel.component.websocket;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ServiceHelper;

public class WebsocketEndpoint extends DefaultEndpoint {

    private NodeSynchronization sync;
    private String remaining;
    private WebsocketStore memoryStore;

    public WebsocketEndpoint(String uri, WebsocketComponent component, String remaining) {
        super(uri, component);
        this.remaining = remaining;

        this.memoryStore = new MemoryWebsocketStore();
        this.sync = new DefaultNodeSynchronization(memoryStore);
    }

    @Override
    public WebsocketComponent getComponent() {
        return (WebsocketComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        WebsocketConsumer consumer = new WebsocketConsumer(this, processor);
        getComponent().addServlet(sync, consumer, remaining);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        getComponent().addServlet(sync, null, remaining);
        return new WebsocketProducer(this, memoryStore);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(memoryStore);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(memoryStore);
        super.doStop();
    }
}
