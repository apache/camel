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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.OwnableState;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.AttachmentQueryCriteria;
import net.corda.core.node.services.vault.AttachmentSort;
import net.corda.core.node.services.vault.ColumnPredicate;
import net.corda.core.node.services.vault.EqualityComparisonOperator;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.Sort;
import net.corda.core.node.services.vault.SortAttribute;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Ignore;
import org.junit.Test;

import static net.corda.core.node.services.vault.QueryCriteriaUtils.DEFAULT_PAGE_NUM;
import static net.corda.core.node.services.vault.QueryCriteriaUtils.MAX_PAGE_SIZE;
import static org.apache.camel.component.corda.CordaConstants.ADD_VAULT_TRANSACTION_NOTE;
import static org.apache.camel.component.corda.CordaConstants.ARGUMENTS;
import static org.apache.camel.component.corda.CordaConstants.ATTACHMENT_EXISTS;
import static org.apache.camel.component.corda.CordaConstants.ATTACHMENT_QUERY_CRITERIA;
import static org.apache.camel.component.corda.CordaConstants.CLEAR_NETWORK_MAP_CACHE;
import static org.apache.camel.component.corda.CordaConstants.CURRENT_NODE_TIME;
import static org.apache.camel.component.corda.CordaConstants.DRAINING_MODE;
import static org.apache.camel.component.corda.CordaConstants.EXACT_MATCH;
import static org.apache.camel.component.corda.CordaConstants.GET_PROTOCOL_VERSION;
import static org.apache.camel.component.corda.CordaConstants.GET_VAULT_TRANSACTION_NOTES;
import static org.apache.camel.component.corda.CordaConstants.IS_FLOWS_DRAINING_MODE_ENABLED;
import static org.apache.camel.component.corda.CordaConstants.NETWORK_MAP_SNAPSHOT;
import static org.apache.camel.component.corda.CordaConstants.NODE_INFO;
import static org.apache.camel.component.corda.CordaConstants.NODE_INFO_FROM_PARTY;
import static org.apache.camel.component.corda.CordaConstants.NOTARY_IDENTITIES;
import static org.apache.camel.component.corda.CordaConstants.NOTARY_PARTY_FROM_X500_NAME;
import static org.apache.camel.component.corda.CordaConstants.OPEN_ATTACHMENT;
import static org.apache.camel.component.corda.CordaConstants.OPERATION;
import static org.apache.camel.component.corda.CordaConstants.PAGE_SPECIFICATION;
import static org.apache.camel.component.corda.CordaConstants.PARTIES_FROM_KEY;
import static org.apache.camel.component.corda.CordaConstants.PARTIES_FROM_NAME;
import static org.apache.camel.component.corda.CordaConstants.QUERY_ATTACHMENTS;
import static org.apache.camel.component.corda.CordaConstants.QUERY_CRITERIA;
import static org.apache.camel.component.corda.CordaConstants.REGISTERED_FLOWS;
import static org.apache.camel.component.corda.CordaConstants.SECURE_HASH;
import static org.apache.camel.component.corda.CordaConstants.SET_FLOWS_DRAINING_MODE_ENABLED;
import static org.apache.camel.component.corda.CordaConstants.SORT;
import static org.apache.camel.component.corda.CordaConstants.START_FLOW_DYNAMIC;
import static org.apache.camel.component.corda.CordaConstants.STATE_MACHINE_RECORDED_TRANSACTION_MAPPING_SNAPSHOT;
import static org.apache.camel.component.corda.CordaConstants.STATE_MACHINE_SNAPSHOT;
import static org.apache.camel.component.corda.CordaConstants.UPLOAD_ATTACHMENT;
import static org.apache.camel.component.corda.CordaConstants.VAULT_QUERY;
import static org.apache.camel.component.corda.CordaConstants.VAULT_QUERY_BY;
import static org.apache.camel.component.corda.CordaConstants.VAULT_QUERY_BY_CRITERIA;
import static org.apache.camel.component.corda.CordaConstants.VAULT_QUERY_BY_WITH_PAGING_SPEC;
import static org.apache.camel.component.corda.CordaConstants.VAULT_QUERY_BY_WITH_SORTING;
import static org.apache.camel.component.corda.CordaConstants.WELL_KNOWN_PARTY_FROM_ANONYMOUS;
import static org.apache.camel.component.corda.CordaConstants.WELL_KNOWN_PARTY_FROM_X500_NAME;

@Ignore("This integration test requires a locally running corda node such cordapp-template-java")
public class CordaProducerTest extends CordaTestSupport {
    private static final SecureHash.SHA256 TEST_SHA_256 = SecureHash.parse("6D1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581F");

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Override
    public boolean isUseAdviceWith() {
        return false;
    }

    @Test
    public void currentNodeTimeTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, CURRENT_NODE_TIME);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void getProtocolVersionTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, GET_PROTOCOL_VERSION);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void networkMapSnapshotTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NETWORK_MAP_SNAPSHOT);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void stateMachinesSnapshotTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, STATE_MACHINE_SNAPSHOT);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void stateMachineRecordedTransactionMappingSnapshotTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, STATE_MACHINE_RECORDED_TRANSACTION_MAPPING_SNAPSHOT);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void registeredFlowsTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, REGISTERED_FLOWS);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void clearNetworkMapCacheTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, CLEAR_NETWORK_MAP_CACHE);
        template.send(exchange);
        Object body = exchange.getException();
        assertNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void isFlowsDrainingModeEnabledTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, IS_FLOWS_DRAINING_MODE_ENABLED);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void setFlowsDrainingModeEnabledTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SET_FLOWS_DRAINING_MODE_ENABLED);
        exchange.getIn().setHeader(DRAINING_MODE, false);
        template.send(exchange);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void notaryIdentitiesTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NOTARY_IDENTITIES);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void nodeInfoTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NODE_INFO);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void addVaultTransactionNoteTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader("Some note", OPERATION, ADD_VAULT_TRANSACTION_NOTE);

        exchange.getIn().setHeader(SECURE_HASH, TEST_SHA_256);
        template.send(exchange);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void getVaultTransactionNotesTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, GET_VAULT_TRANSACTION_NOTES);

        exchange.getIn().setHeader(SECURE_HASH, TEST_SHA_256);
        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void uploadAttachmentTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, UPLOAD_ATTACHMENT);
        exchange.getIn().setBody(zipIt("HELLO" + System.nanoTime(), "test1.txt"));
        template.send(exchange);

        Object body = exchange.getIn().getHeader(SECURE_HASH);
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void attachmentExistsTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ATTACHMENT_EXISTS);
        exchange.getIn().setHeader(SECURE_HASH, TEST_SHA_256);

        template.send(exchange);

        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void openAttachmentTest() throws Exception {
        //Setup node with attachment
        Exchange uploadExchange = createExchangeWithBodyAndHeader(null, OPERATION, UPLOAD_ATTACHMENT);
        uploadExchange.getIn().setBody(zipIt("HELLO" + System.nanoTime(), "test2.txt"));
        template.send(uploadExchange);

        Object hash = uploadExchange.getIn().getHeader(SECURE_HASH);
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, OPEN_ATTACHMENT);
        exchange.getIn().setHeader(SECURE_HASH, hash);

        template.send(exchange);

        InputStream body = exchange.getIn().getBody(InputStream.class);
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void queryAttachmentsTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, QUERY_ATTACHMENTS);
        AttachmentQueryCriteria.AttachmentsQueryCriteria queryCriteria =
                new AttachmentQueryCriteria.AttachmentsQueryCriteria(
                        new ColumnPredicate.EqualityComparison(EqualityComparisonOperator.EQUAL, "Daredevil"));
        AttachmentSort attachmentSort = null;

        exchange.getIn().setHeader(ATTACHMENT_QUERY_CRITERIA, queryCriteria);
        exchange.getIn().setHeader(SORT, attachmentSort);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void nodeInfoFromPartyTest() throws Exception {
        //Expects IntegrationWhiteList is deployed on the node
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NODE_INFO_FROM_PARTY);
        PublicKey pub = generatePublicKey();
        CordaX500Name cordaX500Name1 = new CordaX500Name("PartyA", "London", "GB");
        Party party = new Party(cordaX500Name1, pub);
        exchange.getIn().setBody(party);

        template.send(exchange);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void notaryPartyFromX500NameTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NOTARY_PARTY_FROM_X500_NAME);
        CordaX500Name cordaX500Name = new CordaX500Name("Notary", "London", "GB");
        exchange.getIn().setBody(cordaX500Name);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void partiesFromNameTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader("A", OPERATION, PARTIES_FROM_NAME);
        exchange.getIn().setHeader(EXACT_MATCH, false);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void partyFromKeyTest() throws Exception {
        //Expects IntegrationWhiteList is deployed on the node
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, PARTIES_FROM_KEY);
        PublicKey pub = generatePublicKey();
        exchange.getIn().setBody(pub);

        template.send(exchange);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void wellKnownPartyFromX500NameTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, WELL_KNOWN_PARTY_FROM_X500_NAME);
        CordaX500Name cordaX500Name1 = new CordaX500Name("PartyA", "London", "GB");
        exchange.getIn().setBody(cordaX500Name1);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void wellKnownPartyFromAnonymousTest() throws Exception {
        //Expects IntegrationWhiteList is deployed on the node
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, WELL_KNOWN_PARTY_FROM_ANONYMOUS);
        PublicKey pub = generatePublicKey();

        AbstractParty party = new AnonymousParty(pub);
        exchange.getIn().setBody(party);

        template.send(exchange);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void startFlowDynamicTest() throws Exception {
        //Expects CamelFlow is deployed on the node
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, START_FLOW_DYNAMIC);
        String [] args = new String[] {"Hello"};
        Class<FlowLogic<String>> aClass = (Class<FlowLogic<String>>) Class.forName("org.apache.camel.component.corda.CamelFlow");
        exchange.getIn().setBody(aClass);
        exchange.getIn().setHeader(ARGUMENTS, args);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        assertEquals("Hello world!", body.toString());
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void vaultQueryTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(OwnableState.class, OPERATION, VAULT_QUERY);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void vaultQueryByTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(OwnableState.class, OPERATION, VAULT_QUERY_BY);
        QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED);
        PageSpecification pageSpec = new PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE);
        Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
        Sort sorting = new Sort(ImmutableSet.of(sortByUid));
        exchange.getIn().setHeader(QUERY_CRITERIA, criteria);
        exchange.getIn().setHeader(PAGE_SPECIFICATION, pageSpec);
        exchange.getIn().setHeader(SORT, sorting);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void vaultQueryByCriteriaTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(OwnableState.class, OPERATION, VAULT_QUERY_BY_CRITERIA);
        QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED);
        exchange.getIn().setHeader(QUERY_CRITERIA, criteria);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void vaultQueryByWithPagingSpecTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(OwnableState.class, OPERATION, VAULT_QUERY_BY_WITH_PAGING_SPEC);
        QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED);
        PageSpecification pageSpec = new PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE);
        exchange.getIn().setHeader(QUERY_CRITERIA, criteria);
        exchange.getIn().setHeader(PAGE_SPECIFICATION, pageSpec);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    @Test
    public void vaultQueryByWithSortingTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(OwnableState.class, OPERATION, VAULT_QUERY_BY_WITH_SORTING);
        QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED);
        Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
        Sort sorting = new Sort(ImmutableSet.of(sortByUid));
        exchange.getIn().setHeader(QUERY_CRITERIA, criteria);
        exchange.getIn().setHeader(SORT, sorting);

        template.send(exchange);
        Object body = exchange.getIn().getBody();
        assertNotNull(body);
        Object exception = exchange.getException();
        assertNull(exception);
    }

    private ByteArrayInputStream zipIt(String in, String name) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ZipOutputStream zos = new ZipOutputStream(baos);
            ZipEntry entry = new ZipEntry(name);
            zos.putNextEntry(entry);
            zos.write(in.getBytes());
            zos.closeEntry();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return new ByteArrayInputStream(baos.toByteArray());
    }

    private PublicKey generatePublicKey() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        return kp.getPublic();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to(getUrl());
            }
        };
    }
}
