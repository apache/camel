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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.component.aries.HyperledgerAriesEndpoint;
import org.apache.camel.component.aries.UnsupportedServiceException;
import org.hyperledger.aries.api.present_proof.PresentProofRequest;
import org.hyperledger.aries.api.present_proof.PresentationExchangeRecord;
import org.hyperledger.aries.api.present_proof.PresentationRequest;
import org.hyperledger.aries.api.present_proof.PresentationRequestCredentials;
import org.hyperledger.aries.api.present_proof.PresentationRequestCredentialsFilter;

import static java.util.Collections.emptyList;

public class PresentProofServiceHandler extends AbstractServiceHandler {

    public PresentProofServiceHandler(HyperledgerAriesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange, String service) throws Exception {

        if (service.equals("/present-proof/send-request")) {
            PresentProofRequest reqObj = assertBody(exchange, PresentProofRequest.class);
            PresentationExchangeRecord resObj = createClient().presentProofSendRequest(reqObj).get();
            exchange.getIn().setBody(resObj);

        } else if (service.equals("/present-proof/records")) {
            List<PresentationExchangeRecord> resObj = createClient().presentProofRecords().orElse(emptyList()).stream()
                    .sorted(Collections.reverseOrder((a, b) -> a.getState().ordinal() - b.getState().ordinal()))
                    .peek(pe -> log.info("{}", pe))
                    .collect(Collectors.toList());
            exchange.getIn().setBody(resObj);

        } else if (service.startsWith("/present-proof/records/")) {

            String presExchangeId = getServicePathToken(service, 2);
            if (service.endsWith("/credentials")) {
                PresentationRequestCredentialsFilter reqObj = assertBody(exchange, PresentationRequestCredentialsFilter.class);
                List<PresentationRequestCredentials> resObj
                        = createClient().presentProofRecordsCredentials(presExchangeId, reqObj)
                                .orElse(emptyList());
                exchange.getIn().setBody(resObj);

            } else if (service.endsWith("/send-presentation")) {
                PresentationRequest reqObj = assertBody(exchange, PresentationRequest.class);
                PresentationExchangeRecord resObj
                        = createClient().presentProofRecordsSendPresentation(presExchangeId, reqObj).get();
                exchange.getIn().setBody(resObj);

            } else if (service.endsWith("/verify-presentation")) {
                PresentationExchangeRecord resObj = createClient().presentProofRecordsVerifyPresentation(presExchangeId).get();
                exchange.getIn().setBody(resObj);

            } else if (service.endsWith(presExchangeId)) {
                // /present-proof/records/{pres_ex_id}
                PresentationExchangeRecord resObj = createClient().presentProofRecordsGetById(presExchangeId).orElse(null);
                exchange.getIn().setBody(resObj);

            } else {
                throw new UnsupportedServiceException(service);
            }

        } else if (service.equals("/present-proof/create-request")) {
            PresentProofRequest reqObj = assertBody(exchange, PresentProofRequest.class);
            PresentationExchangeRecord resObj = createClient().presentProofCreateRequest(reqObj).get();
            exchange.getIn().setBody(resObj);
        } else {
            throw new UnsupportedServiceException(service);
        }
    }
}
