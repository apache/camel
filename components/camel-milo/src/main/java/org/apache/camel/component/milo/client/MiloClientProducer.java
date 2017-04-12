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
package org.apache.camel.component.milo.client;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.milo.NamespaceId;
import org.apache.camel.component.milo.PartialNodeId;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiloClientProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientProducer.class);

    private final MiloClientConnection connection;

    private final NamespaceId namespaceId;

    private final PartialNodeId partialNodeId;

    private final boolean defaultAwaitWrites;

    public MiloClientProducer(final Endpoint endpoint, final MiloClientConnection connection, final MiloClientItemConfiguration configuration, final boolean defaultAwaitWrites) {
        super(endpoint);

        this.connection = connection;
        this.defaultAwaitWrites = defaultAwaitWrites;

        this.namespaceId = configuration.makeNamespaceId();
        this.partialNodeId = configuration.makePartialNodeId();
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Message msg = exchange.getIn();
        final Object value = msg.getBody();

        LOG.debug("Processing message: {}", value);

        final Boolean await = msg.getHeader("await", this.defaultAwaitWrites, Boolean.class);

        this.connection.writeValue(this.namespaceId, this.partialNodeId, value, await != null ? await : false);
    }

}
