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
package org.apache.camel.component.milo.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.milo.MiloConstants;
import org.apache.camel.support.DefaultAsyncProducer;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;

import static java.lang.Boolean.TRUE;
import static org.apache.camel.component.milo.MiloConstants.HEADER_NODE_IDS;

public class MiloClientProducer extends DefaultAsyncProducer {

    private MiloClientConnection connection;

    private final ExpandedNodeId nodeId;
    private final ExpandedNodeId methodId;

    private final boolean defaultAwaitWrites;

    public MiloClientProducer(final MiloClientEndpoint endpoint,
                              final boolean defaultAwaitWrites) {
        super(endpoint);

        this.defaultAwaitWrites = defaultAwaitWrites;
        this.nodeId = endpoint.getNodeId();
        this.methodId = endpoint.getMethodId();
    }

    @Override
    public MiloClientEndpoint getEndpoint() {
        return (MiloClientEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.connection = getEndpoint().createConnection();
    }

    @Override
    protected void doStop() throws Exception {
        if (null != this.connection) {
            getEndpoint().releaseConnection(connection);
        }
        super.doStop();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback async) {
        final Message msg = exchange.getIn();
        final Object value = msg.getBody();

        final CompletableFuture<?> future;

        if (msg.getHeaders().containsKey(HEADER_NODE_IDS)) {
            final List<String> nodeIds = msg.getHeader(HEADER_NODE_IDS, List.class);
            final List<ExpandedNodeId> expandedNodeIds
                    = nodeIds.stream().map(String.class::cast).map(ExpandedNodeId::parse).collect(Collectors.toList());
            future = this.connection.readValues(expandedNodeIds).thenApply(nodes -> {
                exchange.getIn().setBody(nodes);
                return nodes;
            });
        } else if (this.methodId == null) {
            future = this.connection.writeValue(this.nodeId, value);
        } else {
            future = this.connection.call(this.nodeId, this.methodId, value);
        }

        final Boolean await = msg.getHeader(MiloConstants.HEADER_AWAIT, this.defaultAwaitWrites, Boolean.class);

        if (TRUE.equals(await)) {
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    msg.getExchange().setException(throwable);
                } else {
                    msg.setBody(result);
                }
                async.done(false);
            });
            return false;
        } else {
            async.done(true);
            return true;
        }
    }

}
