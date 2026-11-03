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
package org.apache.camel.component.zookeeper;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.zookeeper.operations.CreateOperation;
import org.apache.camel.component.zookeeper.operations.DeleteOperation;
import org.apache.camel.component.zookeeper.operations.GetChildrenOperation;
import org.apache.camel.component.zookeeper.operations.OperationResult;
import org.apache.camel.component.zookeeper.operations.SetDataOperation;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getAclListFromMessage;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getCreateMode;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getCreateModeFromString;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getNodeFromMessage;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getPayloadFromExchange;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getVersionFromMessage;

/**
 * <code>ZooKeeperProducer</code> attempts to set the content of nodes in the {@link ZooKeeper} cluster with the
 * payloads of the of the exchanges it receives.
 */
@SuppressWarnings("rawtypes")
public class ZooKeeperProducer extends DefaultProducer {

    public static final String ZK_OPERATION_WRITE = "WRITE";
    public static final String ZK_OPERATION_DELETE = "DELETE";

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperProducer.class);

    private final ZooKeeperConfiguration configuration;
    private ZooKeeperConnectionManager zkm;
    private ZooKeeper connection;

    public ZooKeeperProducer(ZooKeeperEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
        this.zkm = endpoint.getConnectionManager();
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        // a new connection when the session of the previous one expired; a local variable keeps this message on the
        // handle it got here, as other threads may send concurrently
        ZooKeeper zk = this.zkm.getConnection();
        connection = zk;

        String operation = exchange.getIn().getHeader(ZooKeeperMessage.ZOOKEEPER_OPERATION, String.class);
        boolean isDelete = ZK_OPERATION_DELETE.equals(operation);
        ProductionContext context = new ProductionContext(zk, exchange, isDelete);

        // InOnly waits for the confirmation as well, so that a failed write or delete fails the exchange
        OperationResult result;
        if (isDelete) {
            LOG.debug("Deleting znode '{}', waiting for confirmation", context.node);
            result = synchronouslyDelete(context);
        } else {
            LOG.debug("Storing data to znode '{}', waiting for confirmation", context.node);
            result = synchronouslySetData(context);
        }

        if (ExchangeHelper.isOutCapable(exchange)) {
            // the listing of the children must not hide a failed write or delete
            if (result.isOk() && configuration.isListChildren()) {
                result = listChildren(context);
            }
            updateExchangeWithResult(context, result);
        } else if (!result.isOk()) {
            exchange.setException(result.getException());
        }
    }

    @Override
    protected void doStart() throws Exception {
        connection = zkm.getConnection();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Starting zookeeper producer of '{}'", configuration.getPath());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Shutting down zookeeper producer of '{}'", configuration.getPath());
        }
        zkm.shutdown();
    }

    private void updateExchangeWithResult(ProductionContext context, OperationResult result) {
        ZooKeeperMessage out = new ZooKeeperMessage(
                getEndpoint().getCamelContext(), context.node, result.getStatistics(), context.in.getHeaders());
        if (result.isOk()) {
            out.setBody(result.getResult());
        } else {
            context.exchange.setException(result.getException());
        }

        context.exchange.setMessage(out);
    }

    private OperationResult listChildren(ProductionContext context) throws Exception {
        return new GetChildrenOperation(context.connection, configuration.getPath()).get();
    }

    /** Simple container to avoid passing all these around as parameters */
    private class ProductionContext {
        ZooKeeper connection;
        Exchange exchange;
        Message in;
        byte[] payload;
        int version;
        String node;

        ProductionContext(ZooKeeper connection, Exchange exchange, boolean isDelete) throws InvalidPayloadException {
            this.connection = connection;
            this.exchange = exchange;
            this.in = exchange.getIn();
            this.node = getNodeFromMessage(in, configuration.getPath());
            this.version = getVersionFromMessage(in);
            // a DELETE does not read the body
            this.payload = isDelete ? null : getPayloadFromExchange(exchange);
        }
    }

    private OperationResult<String> createNode(ProductionContext ctx) throws Exception {
        CreateOperation create = new CreateOperation(ctx.connection, ctx.node);
        create.setPermissions(getAclListFromMessage(ctx.exchange.getIn()));

        CreateMode mode = null;
        String modeString = configuration.getCreateMode();
        if (modeString != null) {
            try {
                mode = getCreateModeFromString(modeString, CreateMode.EPHEMERAL);
            } catch (Exception e) {
            }
        } else {
            mode = getCreateMode(ctx.exchange.getIn(), CreateMode.EPHEMERAL);
        }
        create.setCreateMode(mode == null ? CreateMode.EPHEMERAL : mode);
        create.setData(ctx.payload);
        return create.get();
    }

    /**
     * Tries to set the data first and if a no node error is received then an attempt will be made to create it instead.
     */
    private OperationResult synchronouslySetData(ProductionContext ctx) throws Exception {

        SetDataOperation setData = new SetDataOperation(ctx.connection, ctx.node, ctx.payload);
        setData.setVersion(ctx.version);

        OperationResult result = setData.get();

        if (!result.isOk() && configuration.isCreate() && result.failedDueTo(Code.NONODE)) {
            LOG.warn("Node '{}' did not exist, creating it.", ctx.node);
            result = createNode(ctx);
        }
        return result;
    }

    /**
     * Deletes the node; a node that does not exist is never created (the create option only applies to writes).
     */
    private OperationResult synchronouslyDelete(ProductionContext ctx) throws Exception {
        DeleteOperation delete = new DeleteOperation(ctx.connection, ctx.node);
        delete.setVersion(ctx.version);
        return delete.get();
    }
}
