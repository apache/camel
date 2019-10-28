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

import java.io.InputStream;
import java.security.PublicKey;

import net.corda.core.contracts.ContractState;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StateMachineRunId;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.services.vault.AttachmentQueryCriteria;
import net.corda.core.node.services.vault.AttachmentSort;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.Sort;
import org.apache.camel.Message;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;

import static org.apache.camel.component.corda.CordaConstants.ARGUMENTS;
import static org.apache.camel.component.corda.CordaConstants.ATTACHMENT_QUERY_CRITERIA;
import static org.apache.camel.component.corda.CordaConstants.DRAINING_MODE;
import static org.apache.camel.component.corda.CordaConstants.EXACT_MATCH;
import static org.apache.camel.component.corda.CordaConstants.PAGE_SPECIFICATION;
import static org.apache.camel.component.corda.CordaConstants.QUERY_CRITERIA;
import static org.apache.camel.component.corda.CordaConstants.SECURE_HASH;
import static org.apache.camel.component.corda.CordaConstants.SORT;

/**
 * The corda producer.
 */

public class CordaProducer extends HeaderSelectorProducer {
    private CordaConfiguration configuration;
    private CordaRPCOps cordaRPCOps;

    public CordaProducer(CordaEndpoint endpoint, final CordaConfiguration configuration, CordaRPCOps cordaRPCOps) {
        super(endpoint, CordaConstants.OPERATION, () -> configuration.getOperation(), false);
        this.configuration = configuration;
        this.cordaRPCOps = cordaRPCOps;
    }

    @Override
    public CordaEndpoint getEndpoint() {
        return (CordaEndpoint) super.getEndpoint();
    }

    @InvokeOnHeader(CordaConstants.CURRENT_NODE_TIME)
    void currentNodeTime(Message message) throws Exception {
        message.setBody(cordaRPCOps.currentNodeTime());
    }

    @InvokeOnHeader(CordaConstants.GET_PROTOCOL_VERSION)
    void getProtocolVersion(Message message) throws Exception {
        message.setBody(cordaRPCOps.getProtocolVersion());
    }

    @InvokeOnHeader(CordaConstants.NETWORK_MAP_SNAPSHOT)
    void networkMapSnapshot(Message message) throws Exception {
        message.setBody(cordaRPCOps.networkMapSnapshot());
    }

    @InvokeOnHeader(CordaConstants.STATE_MACHINE_SNAPSHOT)
    void stateMachinesSnapshot(Message message) throws Exception {
        message.setBody(cordaRPCOps.stateMachinesSnapshot());
    }

    @InvokeOnHeader(CordaConstants.STATE_MACHINE_RECORDED_TRANSACTION_MAPPING_SNAPSHOT)
    void stateMachineRecordedTransactionMappingSnapshot(Message message) throws Exception {
        message.setBody(cordaRPCOps.stateMachineRecordedTransactionMappingSnapshot());
    }

    @InvokeOnHeader(CordaConstants.REGISTERED_FLOWS)
    void registeredFlows(Message message) throws Exception {
        message.setBody(cordaRPCOps.registeredFlows());
    }

    @InvokeOnHeader(CordaConstants.CLEAR_NETWORK_MAP_CACHE)
    void clearNetworkMapCache(Message message) throws Exception {
        cordaRPCOps.clearNetworkMapCache();
    }

    @InvokeOnHeader(CordaConstants.IS_FLOWS_DRAINING_MODE_ENABLED)
    void isFlowsDrainingModeEnabled(Message message) throws Exception {
        message.setBody(cordaRPCOps.isFlowsDrainingModeEnabled());
    }

    @InvokeOnHeader(CordaConstants.SET_FLOWS_DRAINING_MODE_ENABLED)
    void setFlowsDrainingModeEnabled(Message message) throws Exception {
        Boolean mode = message.getHeader(DRAINING_MODE, Boolean.class);
        cordaRPCOps.setFlowsDrainingModeEnabled(mode);
    }

    @InvokeOnHeader(CordaConstants.NOTARY_IDENTITIES)
    void notaryIdentities(Message message) throws Exception {
        message.setBody(cordaRPCOps.notaryIdentities());
    }

    @InvokeOnHeader(CordaConstants.NODE_INFO)
    void nodeInfo(Message message) throws Exception {
        message.setBody(cordaRPCOps.nodeInfo());
    }

    @InvokeOnHeader(CordaConstants.ADD_VAULT_TRANSACTION_NOTE)
    void addVaultTransactionNote(Message message) throws Exception {
        SecureHash secureHash = message.getHeader(SECURE_HASH, SecureHash.class);
        String note = message.getBody(String.class);
        cordaRPCOps.addVaultTransactionNote(secureHash, note);
    }

    @InvokeOnHeader(CordaConstants.GET_VAULT_TRANSACTION_NOTES)
    void getVaultTransactionNotes(Message message) throws Exception {
        SecureHash secureHash = message.getHeader(SECURE_HASH, SecureHash.class);
        message.setBody(cordaRPCOps.getVaultTransactionNotes(secureHash));
    }

    @InvokeOnHeader(CordaConstants.UPLOAD_ATTACHMENT)
    void uploadAttachment(Message message) throws Exception {
        InputStream inputStream = message.getBody(InputStream.class);
        SecureHash secureHash = cordaRPCOps.uploadAttachment(inputStream);
        message.setHeader(SECURE_HASH, secureHash);
    }

    @InvokeOnHeader(CordaConstants.ATTACHMENT_EXISTS)
    void attachmentExists(Message message) throws Exception {
        SecureHash secureHash = message.getHeader(SECURE_HASH, SecureHash.class);
        message.setBody(cordaRPCOps.attachmentExists(secureHash));
    }

    @InvokeOnHeader(CordaConstants.OPEN_ATTACHMENT)
    void openAttachment(Message message) throws Exception {
        SecureHash secureHash = message.getHeader(SECURE_HASH, SecureHash.class);
        message.setBody(cordaRPCOps.openAttachment(secureHash));
    }

    @InvokeOnHeader(CordaConstants.QUERY_ATTACHMENTS)
    void queryAttachments(Message message) throws Exception {
        AttachmentQueryCriteria queryCriteria = message.getHeader(ATTACHMENT_QUERY_CRITERIA, AttachmentQueryCriteria.class);
        AttachmentSort attachmentSort = message.getHeader(SORT, AttachmentSort.class);
        message.setBody(cordaRPCOps.queryAttachments(queryCriteria, attachmentSort));
    }

    @InvokeOnHeader(CordaConstants.NODE_INFO_FROM_PARTY)
    void nodeInfoFromParty(Message message) throws Exception {
        AbstractParty party = message.getBody(AbstractParty.class);
        message.setBody(cordaRPCOps.nodeInfoFromParty(party));
    }

    @InvokeOnHeader(CordaConstants.NOTARY_PARTY_FROM_X500_NAME)
    void notaryPartyFromX500Name(Message message) throws Exception {
        CordaX500Name x500Name = message.getBody(CordaX500Name.class);
        message.setBody(cordaRPCOps.notaryPartyFromX500Name(x500Name));
    }

    @InvokeOnHeader(CordaConstants.PARTIES_FROM_NAME)
    void partiesFromName(Message message) throws Exception {
        String query = message.getBody(String.class);
        Boolean exactMatch = message.getHeader(EXACT_MATCH, Boolean.class);
        message.setBody(cordaRPCOps.partiesFromName(query, exactMatch));
    }

    @InvokeOnHeader(CordaConstants.PARTIES_FROM_KEY)
    void partyFromKey(Message message) throws Exception {
        PublicKey key = message.getBody(PublicKey.class);
        message.setBody(cordaRPCOps.partyFromKey(key));
    }

    @InvokeOnHeader(CordaConstants.WELL_KNOWN_PARTY_FROM_X500_NAME)
    void wellKnownPartyFromX500Name(Message message) throws Exception {
        CordaX500Name x500Name = message.getBody(CordaX500Name.class);
        message.setBody(cordaRPCOps.wellKnownPartyFromX500Name(x500Name));
    }

    @InvokeOnHeader(CordaConstants.WELL_KNOWN_PARTY_FROM_ANONYMOUS)
    void wellKnownPartyFromAnonymous(Message message) throws Exception {
        AbstractParty party = message.getBody(AbstractParty.class);
        message.setBody(cordaRPCOps.wellKnownPartyFromAnonymous(party));
    }

    @InvokeOnHeader(CordaConstants.START_FLOW_DYNAMIC)
    void startFlowDynamic(Message message) throws Exception {
        Object [] args = message.getHeader(ARGUMENTS, Object [].class);
        Class<FlowLogic<?>> aClass = message.getBody(Class.class);
        message.setBody(cordaRPCOps.startFlowDynamic(aClass, args).getReturnValue().get());
    }

    @InvokeOnHeader(CordaConstants.VAULT_QUERY)
    void vaultQuery(Message message) throws Exception {
        Class<ContractState> contractStateClass = message.getBody(Class.class);
        message.setBody(cordaRPCOps.vaultQuery(contractStateClass));
    }

    @InvokeOnHeader(CordaConstants.VAULT_QUERY_BY)
    void vaultQueryBy(Message message) throws Exception {
        Class<ContractState> contractStateClass = message.getBody(Class.class);
        QueryCriteria criteria = message.getHeader(QUERY_CRITERIA, QueryCriteria.class);
        PageSpecification pageSpec = message.getHeader(PAGE_SPECIFICATION, PageSpecification.class);
        Sort sorting = message.getHeader(SORT, Sort.class);
        message.setBody(cordaRPCOps.vaultQueryBy(criteria, pageSpec, sorting, contractStateClass));
    }

    @InvokeOnHeader(CordaConstants.VAULT_QUERY_BY_CRITERIA)
    void vaultQueryByCriteria(Message message) throws Exception {
        Class<ContractState> contractStateClass = message.getBody(Class.class);
        QueryCriteria criteria = message.getHeader(QUERY_CRITERIA, QueryCriteria.class);
        message.setBody(cordaRPCOps.vaultQueryByCriteria(criteria, contractStateClass));
    }

    @InvokeOnHeader(CordaConstants.VAULT_QUERY_BY_WITH_PAGING_SPEC)
    void vaultQueryByWithPagingSpec(Message message) throws Exception {
        Class<ContractState> contractStateClass = message.getBody(Class.class);
        QueryCriteria criteria = message.getHeader(QUERY_CRITERIA, QueryCriteria.class);
        PageSpecification pageSpec = message.getHeader(PAGE_SPECIFICATION, PageSpecification.class);
        message.setBody(cordaRPCOps.vaultQueryByWithPagingSpec(contractStateClass, criteria, pageSpec));
    }

    @InvokeOnHeader(CordaConstants.VAULT_QUERY_BY_WITH_SORTING)
    void vaultQueryByWithSorting(Message message) throws Exception {
        Class<ContractState> contractStateClass = message.getBody(Class.class);
        QueryCriteria criteria = message.getHeader(QUERY_CRITERIA, QueryCriteria.class);
        Sort sorting = message.getHeader(SORT, Sort.class);
        message.setBody(cordaRPCOps.vaultQueryByWithSorting(contractStateClass, criteria, sorting));
    }

    @InvokeOnHeader(CordaConstants.TERMINATE)
    void terminate(Message message) throws Exception {
        cordaRPCOps.terminate(true);
    }

    @InvokeOnHeader(CordaConstants.IS_WAITING_FOR_SHUTDOWN)
    void isWaitingForShutdown(Message message) throws Exception {
        message.setBody(cordaRPCOps.isWaitingForShutdown());
    }

    @InvokeOnHeader(CordaConstants.REFRESH_NETWORK_MAP_CACHE)
    void refreshNetworkMapCache(Message message) throws Exception {
        cordaRPCOps.refreshNetworkMapCache();
    }

    @InvokeOnHeader(CordaConstants.SHUTDOWN)
    void shutdown(Message message) throws Exception {
        cordaRPCOps.shutdown();
    }

    @InvokeOnHeader(CordaConstants.WAIT_UNTIL_NETWORK_READY)
    void waitUntilNetworkReady(Message message) throws Exception {
        message.setBody(cordaRPCOps.waitUntilNetworkReady());
    }

    @InvokeOnHeader(CordaConstants.ACCEPT_NEWNETWORK_PARAMETERS)
    void acceptNewNetworkParameters(Message message) throws Exception {
        SecureHash secureHash = message.getHeader(SECURE_HASH, SecureHash.class);
        cordaRPCOps.acceptNewNetworkParameters(secureHash);
    }

    @InvokeOnHeader(CordaConstants.KILL_FLOW)
    void killFlow(Message message) throws Exception {
        StateMachineRunId stateMachineRunId = message.getBody(StateMachineRunId.class);
        cordaRPCOps.killFlow(stateMachineRunId);
    }

    @InvokeOnHeader(CordaConstants.NETWORK_PARAMETERS_FEED)
    void networkParametersFeed(Message message) throws Exception {
        message.setBody(cordaRPCOps.networkParametersFeed());
    }

    @InvokeOnHeader(CordaConstants.UPLOAD_ATTACHMENT_WITH_META_DATA)
    void uploadAttachmentWithMetadata(Message message) throws Exception {
        InputStream inputStream = message.getBody(InputStream.class);
        String uploader = message.getBody(String.class);
        String filename = message.getBody(String.class);
        SecureHash secureHash = cordaRPCOps.uploadAttachmentWithMetadata(inputStream, uploader, filename);
        message.setHeader(SECURE_HASH, secureHash);
    }
}
