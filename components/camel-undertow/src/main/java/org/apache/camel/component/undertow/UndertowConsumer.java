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
package org.apache.camel.component.undertow;

import io.undertow.server.HttpHandler;

import java.net.URI;

import org.apache.camel.Processor;
import org.apache.camel.component.undertow.handlers.HttpCamelHandler;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Undertow consumer.
 */
public class UndertowConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(UndertowConsumer.class);

    public UndertowConsumer(UndertowEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public UndertowEndpoint getEndpoint() {
        return (UndertowEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Undertow consumer is starting");
        getEndpoint().getComponent().registerConsumer(this);

        UndertowHostFactory factory = UndertowHostFactory.Locator.getUndertowHostFactory();
        if (factory == null) {
            factory = new DefaultUndertowHostFactory();
        }

        URI httpUri = getEndpoint().getHttpURI();
        UndertowHost host = factory.createUndertowHost();

        host.validateEndpointURI(httpUri);
        host.registerHandler(httpUri.getPath(), new HttpCamelHandler(this));
    }

    @Override
    protected void doStop() {
        LOG.debug("Undertow consumer is stopping");
        getEndpoint().getComponent().unregisterConsumer(this);
    }

    class DefaultUndertowHostFactory implements UndertowHostFactory {

        @Override
        public UndertowHost createUndertowHost() {
            return new UndertowHost () {

                @Override
                public void validateEndpointURI(URI httpURI) {
                    // all URIs are good
                }

                @Override
                public void registerHandler(String path, HttpHandler handler) {
                    getEndpoint().getComponent().startServer(UndertowConsumer.this);
                }
            };
        }
    }
}
