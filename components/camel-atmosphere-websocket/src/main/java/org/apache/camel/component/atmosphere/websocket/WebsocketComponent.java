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
package org.apache.camel.component.atmosphere.websocket;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.servlet.ServletComponent;
import org.apache.camel.component.servlet.ServletEndpoint;

/**
 * To exchange data with external Websocket clients using Atmosphere
 */
public class WebsocketComponent extends ServletComponent {
    private Map<String, WebSocketStore> stores;
    
    public WebsocketComponent() {
        // override the default servlet name of ServletComponent
        super(WebsocketEndpoint.class);
        setServletName("CamelWsServlet");
        
        this.stores = new HashMap<String, WebSocketStore>();
    }
    
    @Override
    protected ServletEndpoint createServletEndpoint(String endpointUri, ServletComponent component, URI httpUri) throws Exception {
        return new WebsocketEndpoint(endpointUri, (WebsocketComponent)component, httpUri);
    }
    
    WebSocketStore getWebSocketStore(String name) {
        WebSocketStore store;
        synchronized (stores) {
            store = stores.get(name);
            if (store == null) {
                store = new MemoryWebSocketStore();
                stores.put(name, store);
            }
        }
        return store;
    }
}