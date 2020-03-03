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
package org.apache.camel.websocket.jsr356;

import java.net.URI;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "2.23.0", scheme = "websocket-jsr356", title = "Javax Websocket", syntax = "websocket-jsr356:uri", label = "http")
public class JSR356Endpoint extends DefaultEndpoint {
    @UriPath(description = "If a schemeless URI path is provided, a ServerEndpoint is deployed under that path. "
            + "Else if the URI is prefixed with the 'ws://' scheme, then a connection is established to the corresponding server")
    private URI uri;

    @UriParam(description = "Used when the endpoint is in client mode to populate a pool of sessions", defaultValue = "1")
    private int sessionCount = 1;

    public JSR356Endpoint(final JSR356WebSocketComponent component, final String uri) {
        super(uri, component);
    }

    @Override
    public JSR356WebSocketComponent getComponent() {
        return JSR356WebSocketComponent.class.cast(super.getComponent());
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        Consumer consumer = new JSR356Consumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() {
        return new JSR356Producer(this);
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(final int sessionCount) {
        this.sessionCount = sessionCount;
    }
}
