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
import org.hyperledger.aries.api.schema.SchemaSendRequest;
import org.hyperledger.aries.api.schema.SchemaSendResponse;

public class SchemasServiceHandler extends AbstractServiceHandler {

    public SchemasServiceHandler(HyperledgerAriesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange, String service) throws Exception {

        if (service.equals("/schemas")) {
            SchemaSendRequest schemaReq = assertBody(exchange, SchemaSendRequest.class);
            if (schemaReq.getSchemaName() == null) {
                schemaReq.setSchemaName(endpoint.getConfiguration().getSchemaName());
            }
            if (schemaReq.getSchemaVersion() == null) {
                schemaReq.setSchemaVersion(endpoint.getConfiguration().getSchemaVersion());
            }
            SchemaSendResponse resObj = createClient().schemas(schemaReq).get();
            exchange.getIn().setBody(resObj);

        } else {
            throw new UnsupportedServiceException(service);
        }
    }
}
