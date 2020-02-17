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
package org.apache.camel.component.corda;

import java.util.List;

import net.corda.core.contracts.ContractState;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.messaging.FlowProgressHandle;
import net.corda.core.messaging.StateMachineInfo;
import net.corda.core.messaging.StateMachineTransactionMapping;
import net.corda.core.messaging.StateMachineUpdate;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.NetworkMapCache;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.Sort;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;

import static org.apache.camel.component.corda.CordaConstants.NETWORK_MAP_FEED;
import static org.apache.camel.component.corda.CordaConstants.START_TRACKED_FLOW_DYNAMIC;
import static org.apache.camel.component.corda.CordaConstants.STATE_MACHINE_FEED;
import static org.apache.camel.component.corda.CordaConstants.STATE_MACHINE_RECORDED_TRANSACTION_MAPPING_FEED;
import static org.apache.camel.component.corda.CordaConstants.VAULT_TRACK;
import static org.apache.camel.component.corda.CordaConstants.VAULT_TRACK_BY;
import static org.apache.camel.component.corda.CordaConstants.VAULT_TRACK_BY_CRITERIA;
import static org.apache.camel.component.corda.CordaConstants.VAULT_TRACK_BY_WITH_PAGING_SPEC;
import static org.apache.camel.component.corda.CordaConstants.VAULT_TRACK_BY_WITH_SORTING;

/**
 * The corda consumer.
 */
public class CordaConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(CordaConsumer.class);
    private final CordaConfiguration configuration;
    private CordaRPCOps cordaRPCOps;
    private Subscription subscription;

    public CordaConsumer(CordaEndpoint endpoint, Processor processor, CordaConfiguration configuration, CordaRPCOps cordaRPCOps) {
        super(endpoint, processor);
        this.configuration = configuration;
        this.cordaRPCOps = cordaRPCOps;
    }

    @Override
    public CordaEndpoint getEndpoint() {
        return (CordaEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Exchange exchange = this.getEndpoint().createExchange();
        Class<ContractState> contractStateClass = configuration.getContractStateClass();
        QueryCriteria criteria = configuration.getQueryCriteria();
        PageSpecification pageSpec = configuration.getPageSpecification();
        Sort sorting = configuration.getSort();

        DataFeed<Vault.Page<ContractState>, Vault.Update<ContractState>> pageUpdateDataFeed;
        switch (configuration.getOperation()) {

            case VAULT_TRACK:
                LOG.debug("subscribing for operation: " + VAULT_TRACK);
                pageUpdateDataFeed = cordaRPCOps.vaultTrack(contractStateClass);
                processSnapshot(exchange, pageUpdateDataFeed.getSnapshot());
                subscription = pageUpdateDataFeed.getUpdates().subscribe(
                    x -> processContractStateUpdate(x),
                    t -> processError(t, CordaConstants.VAULT_TRACK),
                    () -> processDone(CordaConstants.VAULT_TRACK)
                );
                break;

            case VAULT_TRACK_BY:
                LOG.debug("subscribing for operation: " + VAULT_TRACK_BY);
                pageUpdateDataFeed = cordaRPCOps.vaultTrackBy(criteria, pageSpec, sorting, contractStateClass);
                processSnapshot(exchange, pageUpdateDataFeed.getSnapshot());
                subscription = pageUpdateDataFeed.getUpdates().subscribe(
                    x -> processContractStateUpdate(x),
                    t -> processError(t, CordaConstants.VAULT_TRACK_BY),
                    () -> processDone(CordaConstants.VAULT_TRACK_BY)
                );
                break;

            case VAULT_TRACK_BY_CRITERIA:
                LOG.debug("subscribing for operation: " + VAULT_TRACK_BY_CRITERIA);
                pageUpdateDataFeed = cordaRPCOps.vaultTrackByCriteria(contractStateClass, criteria);
                processSnapshot(exchange, pageUpdateDataFeed.getSnapshot());
                subscription = pageUpdateDataFeed.getUpdates().subscribe(
                    x -> processContractStateUpdate(x),
                    t -> processError(t, CordaConstants.VAULT_TRACK_BY_CRITERIA),
                    () -> processDone(CordaConstants.VAULT_TRACK_BY_CRITERIA)
                );
                break;

            case VAULT_TRACK_BY_WITH_PAGING_SPEC:
                LOG.debug("subscribing for operation: " + VAULT_TRACK_BY_WITH_PAGING_SPEC);
                pageUpdateDataFeed = cordaRPCOps.vaultTrackByWithPagingSpec(contractStateClass, criteria, pageSpec);
                processSnapshot(exchange, pageUpdateDataFeed.getSnapshot());
                subscription = pageUpdateDataFeed.getUpdates().subscribe(
                    x -> processContractStateUpdate(x),
                    t -> processError(t, CordaConstants.VAULT_TRACK_BY_WITH_PAGING_SPEC),
                    () -> processDone(CordaConstants.VAULT_TRACK_BY_WITH_PAGING_SPEC)
                );
                break;

            case VAULT_TRACK_BY_WITH_SORTING:
                LOG.debug("subscribing for operation: " + VAULT_TRACK_BY_WITH_SORTING);
                pageUpdateDataFeed = cordaRPCOps.vaultTrackByWithSorting(contractStateClass, criteria, sorting);
                processSnapshot(exchange, pageUpdateDataFeed.getSnapshot());
                subscription = pageUpdateDataFeed.getUpdates().subscribe(
                    x -> processContractStateUpdate(x),
                    t -> processError(t, CordaConstants.VAULT_TRACK_BY_WITH_SORTING),
                    () -> processDone(CordaConstants.VAULT_TRACK_BY_WITH_SORTING)
                );
                break;

            case STATE_MACHINE_FEED:
                LOG.debug("subscribing for operation: " + STATE_MACHINE_FEED);
                DataFeed<List<StateMachineInfo>, StateMachineUpdate> stateFeed = cordaRPCOps.stateMachinesFeed();
                processSnapshot(exchange, stateFeed.getSnapshot());
                subscription = stateFeed.getUpdates().subscribe(
                    x -> processStateMachineUpdate(x),
                    t -> processError(t, CordaConstants.STATE_MACHINE_FEED),
                    () -> processDone(CordaConstants.STATE_MACHINE_FEED)
                );
                break;

            case NETWORK_MAP_FEED:
                LOG.debug("subscribing for operation: " + NETWORK_MAP_FEED);

                DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> networkMapFeed = cordaRPCOps.networkMapFeed();
                processSnapshot(exchange, networkMapFeed.getSnapshot());
                subscription = networkMapFeed.getUpdates().subscribe(
                    x -> proceedNetworkMapFeed(x),
                    t -> processError(t, CordaConstants.NETWORK_MAP_FEED),
                    () -> processDone(CordaConstants.NETWORK_MAP_FEED)
                );
                break;

            case STATE_MACHINE_RECORDED_TRANSACTION_MAPPING_FEED:
                LOG.debug("subscribing for operation: " + STATE_MACHINE_RECORDED_TRANSACTION_MAPPING_FEED);

                DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> transactionFeed = cordaRPCOps.stateMachineRecordedTransactionMappingFeed();
                processSnapshot(exchange, transactionFeed.getSnapshot());
                subscription = transactionFeed.getUpdates().subscribe(
                    x -> processTransactionMappingFeed(x),
                    t -> processError(t, CordaConstants.STATE_MACHINE_RECORDED_TRANSACTION_MAPPING_FEED),
                    () -> processDone(CordaConstants.STATE_MACHINE_RECORDED_TRANSACTION_MAPPING_FEED)
                );
                break;

            case START_TRACKED_FLOW_DYNAMIC:
                LOG.debug("subscribing for operation: " + START_TRACKED_FLOW_DYNAMIC);

                FlowProgressHandle<Object> objectFlowProgressHandle = cordaRPCOps.startTrackedFlowDynamic(configuration.getFlowLogicClass(), configuration.getFlowLogicArguments());
                Object result = objectFlowProgressHandle.getReturnValue().get();
                Observable<String> progress = objectFlowProgressHandle.getProgress();
                processSnapshot(exchange, result);
                subscription = progress.subscribe(
                    x -> processFlowProcess(x),
                    t -> processError(t, CordaConstants.START_TRACKED_FLOW_DYNAMIC),
                    () -> processDone(CordaConstants.START_TRACKED_FLOW_DYNAMIC)
                );
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + configuration.getOperation());
        }

        LOG.info("Subscribed: {}", this.configuration);
    }

    private void processSnapshot(Exchange exchange, Object page) {
        if (configuration.isProcessSnapshot()) {
            try {
                exchange.getIn().setBody(page);
                getProcessor().process(exchange);
            } catch (Exception e) {
                LOG.error("Error processing snapshot", e);
            }
        }
    }

    private void processFlowProcess(String x) {
        LOG.debug("processFlowProcess {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void processTransactionMappingFeed(StateMachineTransactionMapping x) {
        LOG.debug("processTransactionMappingFeed {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void proceedNetworkMapFeed(NetworkMapCache.MapChange x) {
        LOG.debug("proceedNetworkMapFeed {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void processStateMachineUpdate(StateMachineUpdate x) {
        LOG.debug("processStateMachineUpdate {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void processContractStateUpdate(Vault.Update<ContractState> x) {
        LOG.debug("processContractStateUpdate {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void processError(Throwable throwable, String operation) {
        LOG.debug("processError for operation: " + operation + " " + throwable);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.setException(throwable);
        processEvent(exchange);
    }

    public void processEvent(Exchange exchange) {
        LOG.debug("processEvent {}", exchange);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            LOG.error("Error processing event ", e);
        }
    }

    private void processDone(String operation) {
        LOG.debug("processDone for operation: {}", operation);
    }

    @Override
    protected void doStop() throws Exception {
        if (subscription != null) {
            subscription.unsubscribe();
        }
        super.doStop();
    }
}
