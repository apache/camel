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

import org.apache.camel.Exchange;
import org.apache.camel.component.aries.HyperledgerAriesEndpoint;
import org.apache.camel.component.aries.UnsupportedServiceException;
import org.hyperledger.acy_py.generated.model.IssuerRevRegRecord;
import org.hyperledger.aries.api.revocation.RevRegCreateRequest;
import org.hyperledger.aries.api.revocation.RevRegCreateResponse.RevocationModuleResponse;
import org.hyperledger.aries.api.revocation.RevokeRequest;

public class RevocationServiceHandler extends AbstractServiceHandler {

    public RevocationServiceHandler(HyperledgerAriesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange, String service) throws Exception {

        if (service.equals("/revocation/create-registry")) {
            RevRegCreateRequest reqObj = assertBody(exchange, RevRegCreateRequest.class);
            IssuerRevRegRecord resObj = createClient().revocationCreateRegistry(reqObj).get();
            exchange.getIn().setBody(resObj);

        } else if (service.equals("/revocation/revoke")) {
            RevokeRequest reqObj = assertBody(exchange, RevokeRequest.class);
            RevocationModuleResponse resObj = createClient().revocationRevoke(reqObj).get();
            exchange.getIn().setBody(resObj);

        } else {
            throw new UnsupportedServiceException(service);
        }
    }
}
