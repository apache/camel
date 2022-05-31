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
package org.apache.camel.component.aries.handler;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.aries.HyperledgerAriesEndpoint;
import org.apache.camel.component.aries.UnsupportedServiceException;
import org.hyperledger.aries.api.issue_credential_v1.IssueCredentialRecordsFilter;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialFreeOfferRequest;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialIssueRequest;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialOfferRequest;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialStoreRequest;

public class IssueCredentialServiceHandler extends AbstractServiceHandler {

    public IssueCredentialServiceHandler(HyperledgerAriesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange, String service) throws Exception {
        if (service.equals("/issue-credential/create-offer")) {
            V1CredentialFreeOfferRequest reqObj = assertBody(exchange, V1CredentialFreeOfferRequest.class);
            V1CredentialExchange resObj = createClient().issueCredentialCreateOffer(reqObj).get();
            exchange.getIn().setBody(resObj);
        } else if (service.equals("/issue-credential/send-offer")) {
            V1CredentialOfferRequest reqObj = assertBody(exchange, V1CredentialOfferRequest.class);
            V1CredentialExchange resObj = createClient().issueCredentialSendOffer(reqObj).get();
            exchange.getIn().setBody(resObj);
        } else if (service.equals("/issue-credential/records")) {
            IssueCredentialRecordsFilter reqObj = assertBody(exchange, IssueCredentialRecordsFilter.class);
            List<V1CredentialExchange> resObj = createClient().issueCredentialRecords(reqObj).get();
            exchange.getIn().setBody(resObj);
        } else if (service.startsWith("/issue-credential/records")) {
            String credentialExchangeId = getServicePathToken(service, 2);
            if (service.endsWith("/send-request")) {
                V1CredentialExchange resObj = createClient().issueCredentialRecordsSendRequest(credentialExchangeId).get();
                exchange.getIn().setBody(resObj);
            } else if (service.endsWith("/issue")) {
                V1CredentialIssueRequest reqObj = maybeBody(exchange, V1CredentialIssueRequest.class);
                V1CredentialExchange resObj = createClient().issueCredentialRecordsIssue(credentialExchangeId, reqObj).get();
                exchange.getIn().setBody(resObj);
            } else if (service.endsWith("/store")) {
                V1CredentialStoreRequest reqObj = maybeBody(exchange, V1CredentialStoreRequest.class);
                V1CredentialExchange resObj = createClient().issueCredentialRecordsStore(credentialExchangeId, reqObj).get();
                exchange.getIn().setBody(resObj);
            } else {
                throw new UnsupportedServiceException(service);
            }
        } else {
            throw new UnsupportedServiceException(service);
        }
    }
}
