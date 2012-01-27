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
import org.apache.camel.util.ObjectHelper;

public class WebsocketEndpoint extends DefaultEndpoint {

    // Todo: Change to Options
    private NodeSynchronization sync;
    private String remaining;

    private WebsocketStore memoryStore;
    private WebsocketStore globalStore;

    private WebsocketConfiguration websocketConfiguration;

    public WebsocketEndpoint() {

    }
    
    public WebsocketEndpoint(String uri, WebsocketComponent component, String remaining, WebsocketConfiguration websocketConfiguration) throws InstantiationException, IllegalAccessException {
        super(uri, component);
        this.remaining = remaining;

        this.memoryStore = new MemoryWebsocketStore();
        // TODO: init globalStore

        this.websocketConfiguration = websocketConfiguration;

        if (websocketConfiguration.getGlobalStore() != null) {
            this.globalStore = (WebsocketStore) ObjectHelper.loadClass(this.websocketConfiguration.getGlobalStore()).newInstance();
        }

        // this.sync = new NodeSynchronizationImpl(this.memoryStore, null);
        this.sync = new NodeSynchronizationImpl(this.memoryStore, this.globalStore);
    }

    public WebsocketStore getMemoryStore() {
        return memoryStore;
    }

    public WebsocketStore getGlobalStore() {
        return globalStore;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {

        // init consumer
        WebsocketConsumer consumer = new WebsocketConsumer(this, processor);

        // register servlet
        ((WebsocketComponent) super.getComponent()).addServlet(this.sync, consumer, this.remaining);

        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {

        // register servlet without consumer
        ((WebsocketComponent) super.getComponent()).addServlet(this.sync, null, this.remaining);

        return new WebsocketProducer(this, this.memoryStore);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    // TODO --> implement store factory
}
