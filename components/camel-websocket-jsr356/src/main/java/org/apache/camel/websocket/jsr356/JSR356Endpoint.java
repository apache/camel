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
package org.apache.camel.websocket.jsr356;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "2.23.0", scheme = "websocket-jsr356", title = "Javax Websocket", syntax = "websocket-jsr356:/resourceUri", label = "jsr356")
public class JSR356Endpoint extends DefaultEndpoint {
    @UriPath(description = "If a path (/foo) it will deploy locally the endpoint, " + "if an uri it will connect to the corresponding server")
    private String websocketPathOrUri;

    @UriParam(description = "Used when the endpoint is in client mode to populate a pool of sessions")
    private int sessionCount = 1;

    @UriParam(description = "the servlet context to use (represented by its path)")
    private String context;

    private final JSR356WebSocketComponent component;

    public JSR356Endpoint(final JSR356WebSocketComponent component, final String uri) {
        super(uri, component);
        this.component = component;
    }

    @Override
    public JSR356WebSocketComponent getComponent() {
        return JSR356WebSocketComponent.class.cast(super.getComponent());
    }

    @Override
    public Consumer createConsumer(final Processor processor) {
        return new JSR356Consumer(this, processor, sessionCount, context);
    }

    @Override
    public Producer createProducer() {
        return new JSR356Producer(this, sessionCount);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(final int sessionCount) {
        this.sessionCount = sessionCount;
    }
}
