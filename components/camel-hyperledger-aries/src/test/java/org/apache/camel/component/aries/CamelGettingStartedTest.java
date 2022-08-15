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
package org.apache.camel.component.aries;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.google.gson.JsonObject;
import io.nessus.aries.wallet.CredentialProposalHelper;
import io.nessus.aries.wallet.NessusWallet;
import io.nessus.aries.websocket.WebSocketClient;
import org.apache.camel.builder.RouteBuilder;
import org.hyperledger.acy_py.generated.model.ConnectionInvitation;
import org.hyperledger.acy_py.generated.model.DID;
import org.hyperledger.acy_py.generated.model.IndyProofReqPredSpec.PTypeEnum;
import org.hyperledger.acy_py.generated.model.IssuerRevRegRecord;
import org.hyperledger.aries.api.connection.ConnectionReceiveInvitationFilter;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.connection.CreateInvitationRequest;
import org.hyperledger.aries.api.connection.CreateInvitationResponse;
import org.hyperledger.aries.api.connection.ReceiveInvitationRequest;
import org.hyperledger.aries.api.credential_definition.CredentialDefinition.CredentialDefinitionRequest;
import org.hyperledger.aries.api.credential_definition.CredentialDefinition.CredentialDefinitionResponse;
import org.hyperledger.aries.api.credentials.CredentialAttributes;
import org.hyperledger.aries.api.credentials.CredentialPreview;
import org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeState;
import org.hyperledger.aries.api.issue_credential_v1.IssueCredentialRecordsFilter;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange.CredentialProposalDict.CredentialProposal;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialIssueRequest;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialOfferRequest;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialStoreRequest;
import org.hyperledger.aries.api.multitenancy.CreateWalletRequest;
import org.hyperledger.aries.api.multitenancy.WalletDispatchType;
import org.hyperledger.aries.api.multitenancy.WalletRecord;
import org.hyperledger.aries.api.multitenancy.WalletType;
import org.hyperledger.aries.api.present_proof.PresentProofRequest;
import org.hyperledger.aries.api.present_proof.PresentProofRequest.ProofRequest;
import org.hyperledger.aries.api.present_proof.PresentProofRequest.ProofRequest.ProofNonRevoked;
import org.hyperledger.aries.api.present_proof.PresentProofRequest.ProofRequest.ProofRequestedAttributes;
import org.hyperledger.aries.api.present_proof.PresentProofRequest.ProofRequest.ProofRequestedPredicates;
import org.hyperledger.aries.api.present_proof.PresentationExchangeRecord;
import org.hyperledger.aries.api.present_proof.PresentationExchangeState;
import org.hyperledger.aries.api.present_proof.PresentationRequest;
import org.hyperledger.aries.api.present_proof.PresentationRequest.IndyRequestedCredsRequestedAttr;
import org.hyperledger.aries.api.present_proof.PresentationRequest.IndyRequestedCredsRequestedPred;
import org.hyperledger.aries.api.present_proof.PresentationRequestCredentials;
import org.hyperledger.aries.api.present_proof.PresentationRequestCredentials.CredentialInfo;
import org.hyperledger.aries.api.present_proof.PresentationRequestCredentialsFilter;
import org.hyperledger.aries.api.revocation.RevRegCreateRequest;
import org.hyperledger.aries.api.revocation.RevRegCreateResponse.RevocationModuleResponse;
import org.hyperledger.aries.api.revocation.RevocationEvent.RevocationEventState;
import org.hyperledger.aries.api.revocation.RevokeRequest;
import org.hyperledger.aries.api.schema.SchemaSendRequest;
import org.hyperledger.aries.api.schema.SchemaSendResponse;
import org.hyperledger.aries.webhook.EventType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.apache.camel.component.aries.Constants.HEADER_MULTITENANCY_LEDGER_ROLE;
import static org.apache.camel.component.aries.Constants.HEADER_MULTITENANCY_SELF_REGISTER_NYM;
import static org.apache.camel.component.aries.Constants.HEADER_MULTITENANCY_TRUSTEE_WALLET;
import static org.apache.camel.component.aries.Constants.HEADER_SERVICE;
import static org.hyperledger.aries.api.ledger.IndyLedgerRoles.ENDORSER;
import static org.hyperledger.aries.api.ledger.IndyLedgerRoles.TRUSTEE;

/**
 * docker compose up --detach && docker compose logs -f acapy
 */
@EnabledIfSystemProperty(named = "enable.hyperledger.aries.itests", matches = "true",
                         disabledReason = "Requires distributed ledger (i.e. blockchain)")
public class CamelGettingStartedTest extends AbstractCamelAriesTest {

    static final String TRANSCRIPT_SCHEMA_ID = "TranscriptSchemaId";
    static final String TRANSCRIPT_CREDEF_ID = "TranscriptCredDefId";
    static final String JOB_CERTIFICATE_SCHEMA_ID = "JobCertificateSchemaId";
    static final String JOB_CERTIFICATE_CREDEF_ID = "JobCertificateCredDefId";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:admin")
                        .to("hyperledger-aries:admin?service=/multitenancy/wallet");

                from("direct:government")
                        .to("hyperledger-aries:government");

                from("direct:faber")
                        .to("hyperledger-aries:faber");

                from("direct:acme")
                        .to("hyperledger-aries:acme");

                from("direct:thrift")
                        .to("hyperledger-aries:thrift");

                from("direct:alice")
                        .to("hyperledger-aries:alice");
            }
        };
    }

    @Test
    public void testWorkflow() throws Exception {

        AttachmentContext ctx = getAttachmentContext();
        setRemoveWalletsOnShutdown(true);

        /*
         * Onboard Government Wallet and DID
         * 
         * Trustees operate nodes. Trustees govern the network. These are the highest
         * privileged DIDs. Endorsers are able to write Schemas and Cred_Defs to the
         * ledger, or sign such transactions so they can be written by non-privileged
         * DIDs.
         * 
         * We want to ensure a DID has the least amount of privilege it needs to
         * operate, which in many cases is no privilege, provided the resources it needs
         * are already written to the ledger, either by a privileged DID or by having
         * the txn signed by a privileged DID (e.g. by an Endorser).
         * 
         * An Endorser is a person or organization that the ledger already knows about,
         * that is able to help bootstrap others.
         */

        onboardGovernment(ctx);

        /*
         * Onboarding Faber, Acme, Thrift
         * 
         * Each connection is actually a pair of Pairwise-Unique Identifiers (DIDs). The
         * one DID is owned by one party to the connection and the second by another.
         * 
         * Both parties know both DIDs and understand what connection this pair
         * describes.
         * 
         * Publishing with a DID verification key allows a person, organization or
         * thing, to verify that someone owns this DID as that person, organization or
         * thing is the only one who knows the corresponding signing key and any
         * DID-related operations requiring signing with this key.
         * 
         * The relationship between them is not shareable with others; it is unique to
         * those two parties in that each pairwise relationship uses different DIDs.
         * 
         * We call the process of establish a connection Onboarding.
         */

        onboardFaberCollege(ctx);
        onboardAcmeCorp(ctx);
        onboardThriftBank(ctx);
        onboardAlice(ctx);

        /*
         * Creating Credential Schemas
         * 
         * Credential Schema is the base semantic structure that describes the list of
         * attributes which one particular Credential can contain.
         * 
         * It’s not possible to update an existing Schema. If the Schema needs to be
         * evolved, a new Schema with a new version or name needs to be created.
         * 
         * Schemas in indy are very simple JSON documents that specify their name and
         * version, and that list attributes that will appear in a credential.
         * Currently, they do not describe data type, recurrence rules, nesting, and
         * other elaborate constructs.
         */

        createTranscriptSchema(ctx);
        createJobCertificateSchema(ctx);

        /*
         * Creating Credential Definitions
         * 
         * Credential Definition is similar in that the keys that the Issuer uses for
         * the signing of Credentials also satisfies a specific Credential Schema.
         * 
         * It references it's associated schema, announces who is going to be issuing
         * credentials with that schema, what type of signature method they plan to use
         * (“CL” = “Camenisch Lysyanskya”, the default method used for zero-knowledge
         * proofs by indy), how they plan to handle revocation, and so forth.
         * 
         * It’s not possible to update data in an existing Credential Definition. If a
         * CredDef needs to be evolved (for example, a key needs to be rotated), then a
         * new Credential Definition needs to be created by a new Issuer DID.
         * 
         * A Credential Definition can be created and saved in the Ledger by an Endorser.
         */

        createTranscriptCredentialDefinition(ctx);
        createJobCertificateCredentialDefinition(ctx);

        /*
         * Create a peer connection between Alice/Faber
         * 
         * Alice does not connect to Faber's public DID, Alice does not even have a public DID
         * Instead both parties create new DIDs that they use for their peer connection 
         */

        connectPeers(ctx, FABER, ALICE);

        /*
         * Alice gets her Transcript from Faber College
         * 
         * A credential is a piece of information about an identity — a name, an age, a
         * credit score... It is information claimed to be true. In this case, the
         * credential is named, “Transcript”.
         * 
         * Credentials are offered by an issuer.
         * 
         * An issuer may be any identity owner known to the Ledger and any issuer may
         * issue a credential about any identity owner it can identify.
         * 
         * The usefulness and reliability of a credential are tied to the reputation of
         * the issuer with respect to the credential at hand. For Alice to self-issue a
         * credential that she likes chocolate ice cream may be perfectly reasonable,
         * but for her to self-issue a credential that she graduated from Faber College
         * should not impress anyone.
         */

        getTranscriptFromFaber(ctx);

        /*
         * Create a peer connection between Alice/Acme
         * 
         *  Alice does not connect to Faber's public DID, Alice does not even have a public DID
         *  Instead both parties create new DIDs that they use for their peer connection 
         */

        connectPeers(ctx, ACME, ALICE);

        /*
         * Alice applies for a job at Acme
         * 
         * At some time in the future, Alice would like to work for Acme Corp. Normally
         * she would browse to their website, where she would click on a hyperlink to
         * apply for a job. Her browser would download a connection request in which her
         * Indy app would open; this would trigger a prompt to Alice, asking her to
         * accept the connection with Acme Corp.
         *
         * After Alice had established connection with Acme, she got the Job-Application
         * Proof Request. A proof request is a request made by the party who needs
         * verifiable proof of having certain attributes and the solving of predicates
         * that can be provided by other verified credentials.
         * 
         * Acme Corp is requesting that Alice provide a Job Application. The Job
         * Application requires a name, degree, status, SSN and also the satisfaction of
         * the condition about the average mark or grades.
         */

        applyForJobWithAcme(ctx);

        /*
         * Alice gets the job and hence receives a JobCertificate from Acme
         * 
         * This is similar to the Transcript VC that she got from Faber, except that the 
         * JobCertificate credential can be revoked by Acme  
         */

        getJobWithAcme(ctx);

        /*
         * Create a peer connection between Alice/Faber
         * 
         * Alice does not connect to Faber's public DID, Alice does not even have a public DID
         * Instead both parties create new DIDs that they use for their peer connection 
         */

        connectPeers(ctx, THRIFT, ALICE);

        /*
         * Alice applies for a loan with Thrift Bank
         * 
         * Now that Alice has a job, she’d like to apply for a loan. That will require a
         * proof of employment. She can get this from the Job-Certificate credential
         * offered by Acme.
         */

        applyForLoanWithThrift(ctx, true);

        /*
         * Thrift accepts the loan application and now requires KYC
         * 
         * Thrift Bank sends the second Proof Request where Alice needs to share her
         * personal information with the bank.
         */

        kycProcessWithThrift(ctx);

        /*
         * Alice decides to quit her job with Acme
         */

        acmeRevokesTheJobCertificate(ctx);

        /*
         * Alice applies for another loan with Thrift Bank - this time without having a Job
         * 
         */

        applyForLoanWithThrift(ctx, false);
    }

    public void onboardGovernment(AttachmentContext ctx) throws IOException {

        String walletName = GOVERNMENT;
        logSection("Onboard " + walletName);

        CreateWalletRequest walletRequest = CreateWalletRequest.builder()
                .walletDispatchType(WalletDispatchType.DEFAULT)
                .walletType(WalletType.INDY)
                .walletName(walletName)
                .walletKey(walletName + "Key")
                .build();

        template.requestBodyAndHeaders("direct:admin", walletRequest, Map.of(
                HEADER_MULTITENANCY_LEDGER_ROLE, TRUSTEE,
                HEADER_MULTITENANCY_SELF_REGISTER_NYM, true),
                WalletRecord.class);

        template.requestBodyAndHeaders("direct:government", null,
                Map.of(HEADER_SERVICE, "/wallet/did/public"), DID.class);

        NessusWallet wallet = assertWallet(walletName);
        wallet.createWebSocketClient(getAgentConfiguration());
        Assertions.assertNotNull(wallet.getPublicDid(), "Public DID not null");
    }

    public void onboardFaberCollege(AttachmentContext ctx) throws IOException {

        String walletName = FABER;
        logSection("Onboard " + walletName);

        CreateWalletRequest walletRequest = CreateWalletRequest.builder()
                .walletDispatchType(WalletDispatchType.DEFAULT)
                .walletType(WalletType.INDY)
                .walletName(walletName)
                .walletKey(walletName + "Key")
                .build();

        template.requestBodyAndHeaders("direct:admin", walletRequest, Map.of(
                HEADER_MULTITENANCY_TRUSTEE_WALLET, GOVERNMENT,
                HEADER_MULTITENANCY_LEDGER_ROLE, ENDORSER),
                WalletRecord.class);

        template.requestBodyAndHeaders("direct:faber", null,
                Map.of(HEADER_SERVICE, "/wallet/did/public"), DID.class);

        NessusWallet wallet = assertWallet(walletName);
        wallet.createWebSocketClient(getAgentConfiguration());
        Assertions.assertNotNull(wallet.getPublicDid(), "Public DID not null");
    }

    public void onboardAcmeCorp(AttachmentContext ctx) throws IOException {

        String walletName = ACME;
        logSection("Onboard " + walletName);

        CreateWalletRequest walletRequest = CreateWalletRequest.builder()
                .walletDispatchType(WalletDispatchType.DEFAULT)
                .walletType(WalletType.INDY)
                .walletName(walletName)
                .walletKey(walletName + "Key")
                .build();

        template.requestBodyAndHeaders("direct:admin", walletRequest, Map.of(
                HEADER_MULTITENANCY_TRUSTEE_WALLET, GOVERNMENT,
                HEADER_MULTITENANCY_LEDGER_ROLE, ENDORSER),
                WalletRecord.class);

        template.requestBodyAndHeaders("direct:acme", null,
                Map.of(HEADER_SERVICE, "/wallet/did/public"), DID.class);

        NessusWallet wallet = assertWallet(walletName);
        wallet.createWebSocketClient(getAgentConfiguration());
        Assertions.assertNotNull(wallet.getPublicDid(), "Public DID not null");
    }

    public void onboardThriftBank(AttachmentContext ctx) throws IOException {

        String walletName = THRIFT;
        logSection("Onboard " + walletName);

        CreateWalletRequest walletRequest = CreateWalletRequest.builder()
                .walletDispatchType(WalletDispatchType.DEFAULT)
                .walletType(WalletType.INDY)
                .walletName(walletName)
                .walletKey(walletName + "Key")
                .build();

        template.requestBodyAndHeaders("direct:admin", walletRequest, Map.of(
                HEADER_MULTITENANCY_TRUSTEE_WALLET, GOVERNMENT,
                HEADER_MULTITENANCY_LEDGER_ROLE, ENDORSER),
                WalletRecord.class);

        template.requestBodyAndHeaders("direct:thrift", null,
                Map.of(HEADER_SERVICE, "/wallet/did/public"), DID.class);

        NessusWallet wallet = assertWallet(walletName);
        wallet.createWebSocketClient(getAgentConfiguration());
        Assertions.assertNotNull(wallet.getPublicDid(), "Public DID not null");
    }

    public void onboardAlice(AttachmentContext ctx) throws IOException {

        String walletName = ALICE;
        logSection("Onboard " + walletName);

        CreateWalletRequest walletRequest = CreateWalletRequest.builder()
                .walletDispatchType(WalletDispatchType.DEFAULT)
                .walletType(WalletType.INDY)
                .walletName(walletName)
                .walletKey(walletName + "Key")
                .build();

        template.requestBody("direct:admin", walletRequest, WalletRecord.class);

        NessusWallet wallet = getWallet(walletName);
        wallet.createWebSocketClient(getAgentConfiguration());
    }

    void connectPeers(AttachmentContext ctx, String inviter, String invitee) throws Exception {

        logSection(String.format("Connect %s to %s", inviter, invitee));

        NessusWallet inviterWallet = getAriesComponent().assertWallet(inviter);
        NessusWallet inviteeWallet = getAriesComponent().assertWallet(invitee);

        WebSocketClient inviterEvents = inviterWallet.getWebSocketClient().startRecording(EventType.CONNECTIONS);
        WebSocketClient inviteeEvents = inviteeWallet.getWebSocketClient().startRecording(EventType.CONNECTIONS);

        // Inviter creates an invitation (/connections/create-invitation)
        UnaryOperator<String> uri = wn -> "direct:" + wn.toLowerCase();
        CreateInvitationRequest createInvitationRequest = CreateInvitationRequest.builder().build();
        CreateInvitationResponse createInvitationResponse = template.requestBodyAndHeaders(uri.apply(inviter),
                createInvitationRequest, Map.of(HEADER_SERVICE, "/connections/create-invitation"),
                CreateInvitationResponse.class);

        // This invitation data finds its way to the Invitee somehow (i.e. out-of-band)
        ConnectionInvitation invitation = createInvitationResponse.getInvitation();

        // Invitee receives the invitation from the Inviter (/connections/receive-invitation)
        ReceiveInvitationRequest receiveInvitationRequest = ReceiveInvitationRequest.builder()
                .recipientKeys(invitation.getRecipientKeys())
                .serviceEndpoint(invitation.getServiceEndpoint())
                .build();

        ConnectionReceiveInvitationFilter receiveParams = ConnectionReceiveInvitationFilter.builder()
                .autoAccept(true)
                .build();

        template.requestBodyAndHeaders(uri.apply(invitee), receiveInvitationRequest, Map.of(
                HEADER_SERVICE, "/connections/receive-invitation",
                ConnectionReceiveInvitationFilter.class.getName(), receiveParams),
                ConnectionRecord.class);

        ConnectionRecord inviterConnectionRecord = inviterEvents
                .awaitConnection(ConnectionRecord::stateIsActive, 10, TimeUnit.SECONDS)
                .findAny().get();

        ConnectionRecord inviteeConnectionRecord = inviteeEvents
                .awaitConnection(ConnectionRecord::stateIsActive, 10, TimeUnit.SECONDS)
                .findAny().get();

        ctx.putAttachment(inviter + invitee + "Connection", inviterConnectionRecord);
        ctx.putAttachment(invitee + inviter + "Connection", inviteeConnectionRecord);
    }

    void createTranscriptSchema(AttachmentContext ctx) {

        logSection("Create Transcript Schema");

        // Faber creates the Transcript Credential Schema and sends it to the Ledger
        // It can do so with it's Endorser role

        SchemaSendRequest schemaRequest = SchemaSendRequest.builder()
                .schemaVersion("1.2")
                .schemaName("Transcript")
                .attributes(Arrays.asList(
                        "first_name",
                        "last_name",
                        "ssn",
                        "degree",
                        "status",
                        "year",
                        "average"))
                .build();

        SchemaSendResponse schemaResponse = template.requestBodyAndHeaders("direct:faber", schemaRequest, Map.of(
                HEADER_SERVICE, "/schemas"),
                SchemaSendResponse.class);
        log.info("{}", schemaResponse);

        ctx.putAttachment(TRANSCRIPT_SCHEMA_ID, schemaResponse.getSchemaId());
    }

    void createJobCertificateSchema(AttachmentContext ctx) {

        logSection("Create Job Certificate Schema");

        // Acme creates the Job-Certificate Credential Schema and sends it to the Ledger
        // It can do so with it's Trustee role

        SchemaSendRequest schemaRequest = SchemaSendRequest.builder()
                .schemaVersion("0.2")
                .schemaName("Job-Certificate")
                .attributes(Arrays.asList(
                        "first_name",
                        "last_name",
                        "salary",
                        "employee_status",
                        "experience"))
                .build();

        SchemaSendResponse schemaResponse = template.requestBodyAndHeaders("direct:acme", schemaRequest, Map.of(
                HEADER_SERVICE, "/schemas"),
                SchemaSendResponse.class);
        log.info("{}", schemaResponse);

        ctx.putAttachment(JOB_CERTIFICATE_SCHEMA_ID, schemaResponse.getSchemaId());
    }

    void createTranscriptCredentialDefinition(AttachmentContext ctx) {

        logSection("Create Transcript CredDef");

        // 1. Faber creates the Transcript Credential Definition

        CredentialDefinitionRequest credDefRequest = CredentialDefinitionRequest.builder()
                .schemaId(ctx.getAttachment(TRANSCRIPT_SCHEMA_ID, String.class))
                .supportRevocation(false)
                .build();

        CredentialDefinitionResponse credDefResponse = template.requestBodyAndHeaders("direct:faber", credDefRequest, Map.of(
                HEADER_SERVICE, "/credential-definitions"),
                CredentialDefinitionResponse.class);
        log.info("{}", credDefResponse);

        ctx.putAttachment(TRANSCRIPT_CREDEF_ID, credDefResponse.getCredentialDefinitionId());
    }

    void createJobCertificateCredentialDefinition(AttachmentContext ctx) {

        logSection("Create Job Certificate CredDef");

        // 1. Acme creates the Job-Certificate Credential Definition

        CredentialDefinitionRequest credDefRequest = CredentialDefinitionRequest.builder()
                .schemaId(ctx.getAttachment(JOB_CERTIFICATE_SCHEMA_ID, String.class))
                .supportRevocation(true)
                .build();

        CredentialDefinitionResponse credDefResponse = template.requestBodyAndHeaders("direct:acme", credDefRequest, Map.of(
                HEADER_SERVICE, "/credential-definitions"),
                CredentialDefinitionResponse.class);
        log.info("{}", credDefResponse);

        String credentialDefinitionId = credDefResponse.getCredentialDefinitionId();
        ctx.putAttachment(JOB_CERTIFICATE_CREDEF_ID, credentialDefinitionId);

        /* 2. Acme creates a Revocation Registry for the given Credential Definition.
         * 
         * The issuer anticipates revoking Job-Certificate credentials. It decides to create a revocation registry.
         * 
         * One of Hyperledger Indy’s revocation registry types uses cryptographic accumulators for publishing revoked credentials. 
         * The use of those accumulators requires the publication of “validity tails” outside of the Ledger.
         */

        RevRegCreateRequest revocRegistryRequest = RevRegCreateRequest.builder()
                .credentialDefinitionId(credentialDefinitionId)
                .build();

        IssuerRevRegRecord revocRegistryRecord = template.requestBodyAndHeaders("direct:acme", revocRegistryRequest, Map.of(
                HEADER_SERVICE, "/revocation/create-registry"),
                IssuerRevRegRecord.class);
        log.info("{}", revocRegistryRecord);
    }

    void getTranscriptFromFaber(AttachmentContext ctx) throws Exception {

        logSection("Alice gets Transcript from Faber");

        String faberAliceConnectionId = ctx.getConnection(FABER, ALICE).getConnectionId();

        WebSocketClient issuerEvents = getWallet(FABER).getWebSocketClient().startRecording(EventType.ISSUE_CREDENTIAL);
        WebSocketClient holderEvents = getWallet(ALICE).getWebSocketClient().startRecording(EventType.ISSUE_CREDENTIAL);

        /* 1. Faber sends the Transcript Credential Offer
         * 
         * The value of this Transcript Credential is that it is provably issued by Faber College
         */

        String transcriptCredDefId = ctx.getAttachment(TRANSCRIPT_CREDEF_ID, String.class);
        V1CredentialOfferRequest credentialOffer = V1CredentialOfferRequest.builder()
                .connectionId(faberAliceConnectionId)
                .credentialDefinitionId(transcriptCredDefId)
                .credentialPreview(new CredentialPreview(
                        CredentialAttributes.from(Map.of(
                                "first_name", "Alice",
                                "last_name", "Garcia",
                                "ssn", "123-45-6789",
                                "degree", "Bachelor of Science, Marketing",
                                "status", "graduated",
                                "year", "2015",
                                "average", "5"))))
                .build();

        template.requestBodyAndHeaders("direct:faber", credentialOffer, Map.of(
                HEADER_SERVICE, "/issue-credential/send-offer"),
                V1CredentialExchange.class);

        /* 2. Alice inspects the the Transcript Credential Offer
         * 
         */

        V1CredentialExchange holderExchange = holderEvents
                .awaitIssueCredentialV1(cex -> cex.getCredentialDefinitionId().equals(transcriptCredDefId) &&
                        cex.getState() == CredentialExchangeState.OFFER_RECEIVED, 10, TimeUnit.SECONDS)
                .findAny().get();

        CredentialProposal credentialProposal = holderExchange.getCredentialProposalDict().getCredentialProposal();
        CredentialProposalHelper credentialHelper = new CredentialProposalHelper(credentialProposal);
        Assertions.assertEquals("Alice", credentialHelper.getAttributeValue("first_name"));
        Assertions.assertEquals("Garcia", credentialHelper.getAttributeValue("last_name"));
        Assertions.assertEquals("graduated", credentialHelper.getAttributeValue("status"));
        Assertions.assertEquals("5", credentialHelper.getAttributeValue("average"));

        /* 3. Alice sends the Transcript Credential Request
         * 
         */

        String holderCredentialExchangeId = holderExchange.getCredentialExchangeId();
        template.requestBodyAndHeaders("direct:alice", null, Map.of(
                HEADER_SERVICE, "/issue-credential/records/" + holderCredentialExchangeId + "/send-request"),
                V1CredentialExchange.class);

        /* 4. Faber receives the Transcript Credential Request
         * 
         */

        V1CredentialExchange issuerExchange = issuerEvents
                .awaitIssueCredentialV1(cex -> cex.getCredentialDefinitionId().equals(transcriptCredDefId) &&
                        cex.getState() == CredentialExchangeState.REQUEST_RECEIVED, 10, TimeUnit.SECONDS)
                .findAny().get();

        /* 5. Faber issues the Transcript Credential
         * 
         */

        String issuerCredentialExchangeId = issuerExchange.getCredentialExchangeId();
        V1CredentialIssueRequest credentialIssueRequest = V1CredentialIssueRequest.builder().build();

        template.requestBodyAndHeaders("direct:faber", credentialIssueRequest, Map.of(
                HEADER_SERVICE, "/issue-credential/records/" + issuerCredentialExchangeId + "/issue"),
                V1CredentialExchange.class);

        /* 6. Alice receives the Transcript Credential
         * 
         */

        holderExchange = holderEvents
                .awaitIssueCredentialV1(cex -> cex.getCredentialDefinitionId().equals(transcriptCredDefId) &&
                        cex.getState() == CredentialExchangeState.CREDENTIAL_RECEIVED, 10, TimeUnit.SECONDS)
                .findAny().get();

        /* 7. Alice stores the Transcript Credential
         * 
         */

        String holderCredentialId = holderExchange.getCredentialId();
        holderCredentialExchangeId = holderExchange.getCredentialExchangeId();
        V1CredentialStoreRequest credentialStoreRequest = V1CredentialStoreRequest.builder()
                .credentialId(holderCredentialId)
                .build();

        template.requestBodyAndHeaders("direct:alice", credentialStoreRequest, Map.of(
                HEADER_SERVICE, "/issue-credential/records/" + holderCredentialExchangeId + "/store"),
                V1CredentialExchange.class);

        holderExchange = holderEvents
                .awaitIssueCredentialV1(cex -> cex.getCredentialDefinitionId().equals(transcriptCredDefId) &&
                        cex.getState() == CredentialExchangeState.CREDENTIAL_ACKED, 10, TimeUnit.SECONDS)
                .findAny().get();
    }

    void applyForJobWithAcme(AttachmentContext ctx) throws Exception {

        logSection("Alice applies for a Job with Acme");

        String acmeAliceConnectionId = ctx.getConnection(ACME, ALICE).getConnectionId();

        WebSocketClient verifierEvents = getWallet(ACME).getWebSocketClient().startRecording(EventType.PRESENT_PROOF);
        WebSocketClient proverEvents = getWallet(ALICE).getWebSocketClient().startRecording(EventType.PRESENT_PROOF);

        /* 1. Acme creates a Job Application Proof Request
         * 
         * Notice that some attributes are verifiable and others are not.
         * 
         * The proof request says that degree, and graduation status, ssn and year must be formally asserted by an issuer and schema_key. 
         * Notice also that the first_name, last_name and phone_number are not required to be verifiable. 
         * 
         * By not tagging these credentials with a verifiable status, Acme’s credential request is saying it will accept 
         * Alice’s own credential about her names and phone number.
         */

        String transcriptCredDefId = ctx.getAttachment(TRANSCRIPT_CREDEF_ID, String.class);

        Function<String, JsonObject> creddefRestriction
                = cdid -> GSON.fromJson("{\"cred_def_id\"=\"" + cdid + "\"}", JsonObject.class);
        Function<String, ProofRequestedAttributes> proofReqAttr = name -> ProofRequestedAttributes.builder().name(name).build();
        BiFunction<String, String, ProofRequestedAttributes> restrictedProofReqAttr
                = (name, cdid) -> ProofRequestedAttributes.builder()
                        .name(name)
                        .restriction(creddefRestriction.apply(cdid))
                        .build();
        BiFunction<String, String, ProofRequestedPredicates> restrictedProofReqPred
                = (pred, cdid) -> ProofRequestedPredicates.builder()
                        .name(pred.split(" ")[0])
                        .pType(PTypeEnum.fromValue(pred.split(" ")[1]))
                        .pValue(Integer.valueOf(pred.split(" ")[2]))
                        .restriction(creddefRestriction.apply(cdid))
                        .build();

        PresentProofRequest proofRequest = PresentProofRequest.builder()
                .connectionId(acmeAliceConnectionId)
                .proofRequest(ProofRequest.builder()
                        .name("Job-Application")
                        .nonce("1")
                        .requestedAttribute("attr1_referent", proofReqAttr.apply("first_name"))
                        .requestedAttribute("attr2_referent", proofReqAttr.apply("last_name"))
                        .requestedAttribute("attr3_referent", restrictedProofReqAttr.apply("ssn", transcriptCredDefId))
                        .requestedAttribute("attr4_referent", restrictedProofReqAttr.apply("degree", transcriptCredDefId))
                        .requestedAttribute("attr5_referent", restrictedProofReqAttr.apply("status", transcriptCredDefId))
                        .requestedAttribute("attr6_referent", restrictedProofReqAttr.apply("year", transcriptCredDefId))
                        .requestedPredicate("pred1_referent", restrictedProofReqPred.apply("average >= 4", transcriptCredDefId))
                        .build())
                .build();

        PresentationExchangeRecord verifierExchangeRecord = template.requestBodyAndHeaders("direct:acme", proofRequest, Map.of(
                HEADER_SERVICE, "/present-proof/send-request"),
                PresentationExchangeRecord.class);

        String threadId = verifierExchangeRecord.getThreadId();
        PresentationExchangeRecord proverExchangeRecord = proverEvents
                .awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.REQUEST_RECEIVED &&
                        pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        // 2. Alice searches her Wallet for Credentials that she can use for the creating of Proof for the Job-Application Proof Request

        Map<String, String> referentMapping = new HashMap<>();
        String proverExchangeId = proverExchangeRecord.getPresentationExchangeId();
        PresentationRequestCredentialsFilter credentialsFilter = PresentationRequestCredentialsFilter.builder().build();

        List<PresentationRequestCredentials> credentials = Arrays.asList(template.requestBodyAndHeaders("direct:alice",
                credentialsFilter, Map.of(
                        HEADER_SERVICE, "/present-proof/records/" + proverExchangeId + "/credentials"),
                PresentationRequestCredentials[].class));
        Assertions.assertEquals(1, credentials.size(), "Expected single Credential");

        credentials.stream().forEach(cred -> {
            List<String> presentationReferents = cred.getPresentationReferents();
            CredentialInfo credInfo = cred.getCredentialInfo();
            String credDefId = credInfo.getCredentialDefinitionId();
            Map<String, String> attributes = credInfo.getAttrs();
            String referent = credInfo.getReferent();
            log.info("{}", cred);
            log.info("+- CredDefId: {}", credDefId);
            log.info("+- PresentationReferents: {}", presentationReferents);
            log.info("+- Attributes: {}", attributes);
            log.info("+- Referent: {}", referent);

            // Map attribute referents to their respective credential referent
            presentationReferents.stream().forEach(pr -> referentMapping.put(pr, referent));
        });

        /* 3. Alice provides Job Application Proof
         * 
         * Alice divides these attributes into the three groups:
         * 
         * - attributes values of which will be revealed
         * - attributes values of which will be unrevealed
         * - attributes for which creating of verifiable proof is not required
         */

        BiFunction<String, Boolean, IndyRequestedCredsRequestedAttr> indyRequestedAttr
                = (ref, reveal) -> IndyRequestedCredsRequestedAttr.builder()
                        .credId(referentMapping.get(ref))
                        .revealed(reveal)
                        .build();
        Function<String, IndyRequestedCredsRequestedPred> indyRequestedPred = ref -> IndyRequestedCredsRequestedPred.builder()
                .credId(referentMapping.get(ref))
                .build();

        PresentationRequest presentationRequest = PresentationRequest.builder()
                .selfAttestedAttributes(Map.of(
                        "attr1_referent", "Alice",
                        "attr2_referent", "Garcia"))
                .requestedAttributes(Map.of(
                        "attr3_referent", indyRequestedAttr.apply("attr3_referent", true),
                        "attr4_referent", indyRequestedAttr.apply("attr4_referent", true),
                        "attr5_referent", indyRequestedAttr.apply("attr5_referent", true),
                        "attr6_referent", indyRequestedAttr.apply("attr6_referent", true)))
                .requestedPredicates(Map.of(
                        "pred1_referent", indyRequestedPred.apply("pred1_referent")))
                .build();

        template.requestBodyAndHeaders("direct:alice", presentationRequest, Map.of(
                HEADER_SERVICE, "/present-proof/records/" + proverExchangeId + "/send-presentation"),
                PresentationExchangeRecord.class);

        verifierExchangeRecord = verifierEvents
                .awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.PRESENTATION_RECEIVED &&
                        pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        /* 4. Acme verifies the Job Application Proof from Alice
         * 
         */

        String verifierExchangeId = verifierExchangeRecord.getPresentationExchangeId();

        template.requestBodyAndHeaders("direct:acme", null, Map.of(
                HEADER_SERVICE, "/present-proof/records/" + verifierExchangeId + "/verify-presentation"),
                PresentationExchangeRecord.class);

        verifierExchangeRecord = verifierEvents
                .awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.VERIFIED &&
                        pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        Assertions.assertTrue(verifierExchangeRecord.isVerified(), "Not VERIFIED");

        proverEvents.awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.PRESENTATION_ACKED &&
                pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        // [#1754] Prover cannot know verification outcome from PRESENTATION_ACKED
        // https://github.com/hyperledger/aries-cloudagent-python/issues/1754
        //Assertions.assertTrue(proverExchangeRecord[0].isVerified(), "Not VERIFIED");
    }

    void getJobWithAcme(AttachmentContext ctx) throws Exception {

        logSection("Alice gets JobCertificate from Acme");

        String acmeAliceConnectionId = ctx.getConnection(ACME, ALICE).getConnectionId();

        WebSocketClient issuerEvents = getWallet(ACME).getWebSocketClient().startRecording(EventType.ISSUE_CREDENTIAL);
        WebSocketClient holderEvents = getWallet(ALICE).getWebSocketClient().startRecording(EventType.ISSUE_CREDENTIAL);

        /* 1. Acme sends the JobCertificate Credential Offer
         * 
         * The value of this JobCertificate Credential is that it 
         * is verifiably proves that the holder is employed by Acme
         */

        String jobCertificateCredDefId = ctx.getAttachment(JOB_CERTIFICATE_CREDEF_ID, String.class);
        V1CredentialOfferRequest credentialOfferRequest = V1CredentialOfferRequest.builder()
                .connectionId(acmeAliceConnectionId)
                .credentialDefinitionId(jobCertificateCredDefId)
                .credentialPreview(new CredentialPreview(
                        CredentialAttributes.from(Map.of(
                                "first_name", "Alice",
                                "last_name", "Garcia",
                                "employee_status", "Permanent",
                                "experience", "10",
                                "salary", "2400"))))
                .build();

        template.requestBodyAndHeaders("direct:acme", credentialOfferRequest, Map.of(
                HEADER_SERVICE, "/issue-credential/send-offer"),
                V1CredentialExchange.class);

        /* 2. Alice inspects the the JobCertificate Credential Offer
         * 
         */

        V1CredentialExchange holderExchange = holderEvents
                .awaitIssueCredentialV1(cex -> cex.getCredentialDefinitionId().equals(jobCertificateCredDefId) &&
                        cex.getState() == CredentialExchangeState.OFFER_RECEIVED, 10, TimeUnit.SECONDS)
                .findAny().get();

        CredentialProposal credentialProposal = holderExchange.getCredentialProposalDict().getCredentialProposal();
        CredentialProposalHelper credentialHelper = new CredentialProposalHelper(credentialProposal);
        Assertions.assertEquals("Alice", credentialHelper.getAttributeValue("first_name"));
        Assertions.assertEquals("Garcia", credentialHelper.getAttributeValue("last_name"));
        Assertions.assertEquals("Permanent", credentialHelper.getAttributeValue("employee_status"));
        Assertions.assertEquals("2400", credentialHelper.getAttributeValue("salary"));

        /* 3. Alice sends the JobCertificate Credential Request
         * 
         */

        String holderCredentialExchangeId = holderExchange.getCredentialExchangeId();
        template.requestBodyAndHeaders("direct:alice", null, Map.of(
                HEADER_SERVICE, "/issue-credential/records/" + holderCredentialExchangeId + "/send-request"),
                V1CredentialExchange.class);

        /* 4. Acme receives the JobCertificate Credential Request
         * 
         */

        V1CredentialExchange issuerExchange = issuerEvents
                .awaitIssueCredentialV1(cex -> cex.getCredentialDefinitionId().equals(jobCertificateCredDefId) &&
                        cex.getState() == CredentialExchangeState.REQUEST_RECEIVED, 10, TimeUnit.SECONDS)
                .findAny().get();

        /* 5. Acme issues the JobCertificate Credential
         * 
         */

        String issuerCredentialExchangeId = issuerExchange.getCredentialExchangeId();
        V1CredentialIssueRequest credentialIssueRequest = V1CredentialIssueRequest.builder().build();

        template.requestBodyAndHeaders("direct:acme", credentialIssueRequest, Map.of(
                HEADER_SERVICE, "/issue-credential/records/" + issuerCredentialExchangeId + "/issue"),
                V1CredentialExchange.class);

        /* 6. Alice receives the Transcript Credential
         * 
         */

        holderExchange = holderEvents
                .awaitIssueCredentialV1(cex -> cex.getCredentialDefinitionId().equals(jobCertificateCredDefId) &&
                        cex.getState() == CredentialExchangeState.CREDENTIAL_RECEIVED, 10, TimeUnit.SECONDS)
                .findAny().get();

        /* 7. Alice stores the Transcript Credential
         * 
         */

        String holderCredentialId = holderExchange.getCredentialId();
        holderCredentialExchangeId = holderExchange.getCredentialExchangeId();
        V1CredentialStoreRequest credentialStoreRequest = V1CredentialStoreRequest.builder()
                .credentialId(holderCredentialId)
                .build();

        template.requestBodyAndHeaders("direct:alice", credentialStoreRequest, Map.of(
                HEADER_SERVICE, "/issue-credential/records/" + holderCredentialExchangeId + "/store"),
                V1CredentialExchange.class);

        holderExchange = holderEvents
                .awaitIssueCredentialV1(cex -> cex.getCredentialDefinitionId().equals(jobCertificateCredDefId) &&
                        cex.getState() == CredentialExchangeState.CREDENTIAL_ACKED, 10, TimeUnit.SECONDS)
                .findAny().get();
    }

    void applyForLoanWithThrift(AttachmentContext ctx, boolean expectedOutcome) throws Exception {

        logSection("Alice applies for a Loan with Thrift");

        String thriftAliceConnectionId = ctx.getConnection(THRIFT, ALICE).getConnectionId();

        WebSocketClient verifierEvents = getWallet(THRIFT).getWebSocketClient().startRecording(EventType.PRESENT_PROOF);
        WebSocketClient proverEvents = getWallet(ALICE).getWebSocketClient().startRecording(EventType.PRESENT_PROOF);

        /* 1. Alice gets a Loan-Application Proof Request from Thrift Bank
         * 
         * Note, that the Job-Certificate should not have been revoked at the time of application.
         */

        String jobCertificateCredDefId = ctx.getAttachment(JOB_CERTIFICATE_CREDEF_ID, String.class);

        Function<String, JsonObject> credDefRestriction
                = cdid -> GSON.fromJson("{\"cred_def_id\"=\"" + cdid + "\"}", JsonObject.class);
        BiFunction<String, String, ProofRequestedAttributes> restrictedProofReqAttr
                = (name, cdid) -> ProofRequestedAttributes.builder()
                        .name(name)
                        .restriction(credDefRestriction.apply(cdid))
                        .build();
        BiFunction<String, String, ProofRequestedPredicates> restrictedProofReqPred
                = (pred, cdid) -> ProofRequestedPredicates.builder()
                        .name(pred.split(" ")[0])
                        .pType(PTypeEnum.fromValue(pred.split(" ")[1]))
                        .pValue(Integer.valueOf(pred.split(" ")[2]))
                        .restriction(credDefRestriction.apply(cdid))
                        .build();

        PresentProofRequest proofRequest = PresentProofRequest.builder()
                .connectionId(thriftAliceConnectionId)
                .proofRequest(ProofRequest.builder()
                        .name("Loan-Application")
                        .nonce("1")
                        .requestedAttribute("attr1_referent",
                                restrictedProofReqAttr.apply("employee_status", jobCertificateCredDefId))
                        .requestedPredicate("pred1_referent",
                                restrictedProofReqPred.apply("salary >= 2000", jobCertificateCredDefId))
                        .requestedPredicate("pred2_referent",
                                restrictedProofReqPred.apply("experience >= 1", jobCertificateCredDefId))
                        .nonRevoked(ProofNonRevoked.builder()
                                .from(Instant.now().getEpochSecond())
                                .to(Instant.now().getEpochSecond())
                                .build())
                        .build())
                .build();

        PresentationExchangeRecord verifierExchangeRecord = template.requestBodyAndHeaders("direct:thrift", proofRequest,
                Map.of(
                        HEADER_SERVICE, "/present-proof/send-request"),
                PresentationExchangeRecord.class);

        String threadId = verifierExchangeRecord.getThreadId();
        PresentationExchangeRecord proverExchangeRecord = proverEvents
                .awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.REQUEST_RECEIVED &&
                        pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        // 2. Alice searches her Wallet for Credentials that she can use for the creating of Proof for the Loan-Application Proof Request

        Map<String, String> referentMapping = new HashMap<>();
        String proverExchangeId = proverExchangeRecord.getPresentationExchangeId();
        PresentationRequestCredentialsFilter credentialsFilter = PresentationRequestCredentialsFilter.builder().build();

        List<PresentationRequestCredentials> credentials = Arrays.asList(template.requestBodyAndHeaders("direct:alice",
                credentialsFilter, Map.of(
                        HEADER_SERVICE, "/present-proof/records/" + proverExchangeId + "/credentials"),
                PresentationRequestCredentials[].class));
        Assertions.assertEquals(1, credentials.size(), "Expected single Credential");

        credentials.stream().forEach(cred -> {
            List<String> presentationReferents = cred.getPresentationReferents();
            CredentialInfo credInfo = cred.getCredentialInfo();
            String credDefId = credInfo.getCredentialDefinitionId();
            Map<String, String> attributes = credInfo.getAttrs();
            String referent = credInfo.getReferent();
            log.info("{}", cred);
            log.info("+- CredDefId: {}", credDefId);
            log.info("+- PresentationReferents: {}", presentationReferents);
            log.info("+- Attributes: {}", attributes);
            log.info("+- Referent: {}", referent);

            // Map attribute referents to their respective credential referent
            presentationReferents.stream().forEach(pr -> referentMapping.put(pr, referent));
        });

        /* 3. Alice provides Loan-Application Proof
         * 
         * Alice divides these attributes into the three groups:
         * 
         * - attributes values of which will be revealed
         * - attributes values of which will be unrevealed
         * - attributes for which creating of verifiable proof is not required
         */

        BiFunction<String, Boolean, IndyRequestedCredsRequestedAttr> indyRequestedAttr
                = (ref, reveal) -> IndyRequestedCredsRequestedAttr.builder()
                        .credId(referentMapping.get(ref))
                        .revealed(reveal)
                        .build();
        Function<String, IndyRequestedCredsRequestedPred> indyRequestedPred = ref -> IndyRequestedCredsRequestedPred.builder()
                .credId(referentMapping.get(ref))
                .build();

        PresentationRequest presentationRequest = PresentationRequest.builder()
                .requestedAttributes(Map.of(
                        "attr1_referent", indyRequestedAttr.apply("attr1_referent", true)))
                .requestedPredicates(Map.of(
                        "pred1_referent", indyRequestedPred.apply("pred1_referent"),
                        "pred2_referent", indyRequestedPred.apply("pred2_referent")))
                .build();

        template.requestBodyAndHeaders("direct:alice", presentationRequest, Map.of(
                HEADER_SERVICE, "/present-proof/records/" + proverExchangeId + "/send-presentation"),
                PresentationExchangeRecord.class);

        verifierExchangeRecord = verifierEvents
                .awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.PRESENTATION_RECEIVED &&
                        pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        /* 4. Thrift verifies the Loan-Application Proof from Alice
         * 
         */

        String verifierExchangeId = verifierExchangeRecord.getPresentationExchangeId();

        template.requestBodyAndHeaders("direct:thrift", null, Map.of(
                HEADER_SERVICE, "/present-proof/records/" + verifierExchangeId + "/verify-presentation"),
                PresentationExchangeRecord.class);

        verifierExchangeRecord = verifierEvents
                .awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.VERIFIED &&
                        pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        Assertions.assertEquals(expectedOutcome, verifierExchangeRecord.isVerified(), "Unexpected verification outcome");

        proverEvents.awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.PRESENTATION_ACKED &&
                pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        // [#1754] Prover cannot know verification outcome from PRESENTATION_ACKED
        // https://github.com/hyperledger/aries-cloudagent-python/issues/1754
        //Assertions.assertTrue(proverExchangeRecord[0].isVerified(), "Not VERIFIED");
    }

    void kycProcessWithThrift(AttachmentContext ctx) throws Exception {

        logSection("Alice goes through the KYC process with Thrift");

        String thriftAliceConnectionId = ctx.getConnection(THRIFT, ALICE).getConnectionId();

        WebSocketClient verifierEvents = getWallet(THRIFT).getWebSocketClient().startRecording(EventType.PRESENT_PROOF);
        WebSocketClient proverEvents = getWallet(ALICE).getWebSocketClient().startRecording(EventType.PRESENT_PROOF);

        /* 1. Alice gets a second Proof Request from Thrift Bank
         * 
         */

        String transcriptCredDefId = ctx.getAttachment(TRANSCRIPT_CREDEF_ID, String.class);

        Function<String, JsonObject> credDefRestriction
                = cdid -> GSON.fromJson("{\"cred_def_id\"=\"" + cdid + "\"}", JsonObject.class);
        BiFunction<String, String, ProofRequestedAttributes> restrictedProofReqAttr
                = (name, cdid) -> ProofRequestedAttributes.builder()
                        .name(name)
                        .restriction(credDefRestriction.apply(cdid))
                        .build();

        PresentProofRequest proofRequest = PresentProofRequest.builder()
                .connectionId(thriftAliceConnectionId)
                .proofRequest(ProofRequest.builder()
                        .name("KYC-Application")
                        .nonce("1")
                        .requestedAttribute("attr1_referent", restrictedProofReqAttr.apply("first_name", transcriptCredDefId))
                        .requestedAttribute("attr2_referent", restrictedProofReqAttr.apply("last_name", transcriptCredDefId))
                        .requestedAttribute("attr3_referent", restrictedProofReqAttr.apply("ssn", transcriptCredDefId))
                        .build())
                .build();

        PresentationExchangeRecord verifierExchangeRecord = template.requestBodyAndHeaders("direct:thrift", proofRequest,
                Map.of(
                        HEADER_SERVICE, "/present-proof/send-request"),
                PresentationExchangeRecord.class);

        String threadId = verifierExchangeRecord.getThreadId();
        PresentationExchangeRecord proverExchangeRecord = proverEvents
                .awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.REQUEST_RECEIVED &&
                        pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        // 2. Alice searches her Wallet for Credentials that she can use for the creating of Proof for the KYC Proof Request

        Map<String, String> referentMapping = new HashMap<>();
        String proverExchangeId = proverExchangeRecord.getPresentationExchangeId();
        PresentationRequestCredentialsFilter credentialsFilter = PresentationRequestCredentialsFilter.builder().build();

        List<PresentationRequestCredentials> credentials = Arrays.asList(template.requestBodyAndHeaders("direct:alice",
                credentialsFilter, Map.of(
                        HEADER_SERVICE, "/present-proof/records/" + proverExchangeId + "/credentials"),
                PresentationRequestCredentials[].class));
        Assertions.assertEquals(1, credentials.size(), "Expected single Credential");

        credentials.stream().forEach(cred -> {
            List<String> presentationReferents = cred.getPresentationReferents();
            CredentialInfo credInfo = cred.getCredentialInfo();
            String credDefId = credInfo.getCredentialDefinitionId();
            Map<String, String> attributes = credInfo.getAttrs();
            String referent = credInfo.getReferent();
            log.info("{}", cred);
            log.info("+- CredDefId: {}", credDefId);
            log.info("+- PresentationReferents: {}", presentationReferents);
            log.info("+- Attributes: {}", attributes);
            log.info("+- Referent: {}", referent);

            // Map attribute referents to their respective credential referent
            presentationReferents.stream().forEach(pr -> referentMapping.put(pr, referent));
        });

        /* 3. Alice provides KYC-Application Proof
         * 
         * Alice divides these attributes into the three groups:
         * 
         * - attributes values of which will be revealed
         * - attributes values of which will be unrevealed
         * - attributes for which creating of verifiable proof is not required
         */

        BiFunction<String, Boolean, IndyRequestedCredsRequestedAttr> indyRequestedAttr
                = (ref, reveal) -> IndyRequestedCredsRequestedAttr.builder()
                        .credId(referentMapping.get(ref))
                        .revealed(reveal)
                        .build();

        PresentationRequest presentationRequest = PresentationRequest.builder()
                .requestedAttributes(Map.of(
                        "attr1_referent", indyRequestedAttr.apply("attr1_referent", true),
                        "attr2_referent", indyRequestedAttr.apply("attr2_referent", true),
                        "attr3_referent", indyRequestedAttr.apply("attr3_referent", true)))
                .build();

        template.requestBodyAndHeaders("direct:alice", presentationRequest, Map.of(
                HEADER_SERVICE, "/present-proof/records/" + proverExchangeId + "/send-presentation"),
                PresentationExchangeRecord.class);

        verifierExchangeRecord = verifierEvents
                .awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.PRESENTATION_RECEIVED &&
                        pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        /* 4. Thrift verifies the KYC-Application Proof from Alice
         * 
         */

        String verifierExchangeId = verifierExchangeRecord.getPresentationExchangeId();

        template.requestBodyAndHeaders("direct:thrift", null, Map.of(
                HEADER_SERVICE, "/present-proof/records/" + verifierExchangeId + "/verify-presentation"),
                PresentationExchangeRecord.class);

        verifierExchangeRecord = verifierEvents
                .awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.VERIFIED &&
                        pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();
        Assertions.assertTrue(verifierExchangeRecord.isVerified(), "Not VERIFIED");

        proverEvents.awaitPresentProofV1(pex -> pex.getState() == PresentationExchangeState.PRESENTATION_ACKED &&
                pex.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
                .findAny().get();

        // [#1754] Prover cannot know verification outcome from PRESENTATION_ACKED
        // https://github.com/hyperledger/aries-cloudagent-python/issues/1754
        //Assertions.assertTrue(proverExchangeRecord[0].isVerified(), "Not VERIFIED");
    }

    void acmeRevokesTheJobCertificate(AttachmentContext ctx) throws Exception {

        logSection("Acme revokes the Job-Certificate Credential");

        String acmeAliceConnectionId = ctx.getConnection(ACME, ALICE).getConnectionId();

        WebSocketClient issuerEvents
                = getWallet(ACME).getWebSocketClient().startRecording(EventType.ISSUER_CRED_REV, EventType.ISSUE_CREDENTIAL);

        // [TODO] There is currently no event that the Holder could listen to
        // WebSocketClient holderEvents = assertWallet(Alice).getWebSocketClient();   

        // 1. Acme searches the Job-Certificate Credential

        String jobCertificateCredDefId = ctx.getAttachment(JOB_CERTIFICATE_CREDEF_ID, String.class);
        IssueCredentialRecordsFilter recordsFilter = IssueCredentialRecordsFilter.builder()
                .connectionId(acmeAliceConnectionId)
                .build();

        V1CredentialExchange credex = Arrays.asList(template.requestBodyAndHeaders("direct:acme", recordsFilter, Map.of(
                HEADER_SERVICE, "/issue-credential/records"),
                V1CredentialExchange[].class)).stream()
                .filter(cr -> jobCertificateCredDefId.equals(cr.getCredentialDefinitionId()))
                .findFirst().get();

        // 2. Acme revokes the Job-Certificate Credential

        RevokeRequest revokeRequest = RevokeRequest.builder()
                .credExId(credex.getCredentialExchangeId())
                .connectionId(acmeAliceConnectionId)
                .publish(true)
                .notify(true)
                .build();

        template.requestBodyAndHeaders("direct:acme", revokeRequest, Map.of(
                HEADER_SERVICE, "/revocation/revoke"),
                RevocationModuleResponse.class);

        issuerEvents.awaitIssuerRevocation(rev -> rev.getCredDefId().equals(jobCertificateCredDefId) &&
                rev.getState() == RevocationEventState.REVOKED, 10, TimeUnit.SECONDS)
                .findAny().get();

        issuerEvents.awaitIssueCredentialV1(cex -> cex.getCredentialDefinitionId().equals(jobCertificateCredDefId) &&
                cex.getState() == CredentialExchangeState.CREDENTIAL_REVOKED, 10, TimeUnit.SECONDS)
                .findAny().get();
    }
}
