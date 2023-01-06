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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.nessus.aries.util.AssertState;
import org.apache.camel.Exchange;
import org.apache.camel.component.aries.HyperledgerAriesEndpoint;
import org.apache.camel.component.aries.UnsupportedServiceException;
import org.hyperledger.acy_py.generated.model.DID;
import org.hyperledger.aries.api.credential_definition.CredentialDefinition.CredentialDefinitionRequest;
import org.hyperledger.aries.api.credential_definition.CredentialDefinition.CredentialDefinitionResponse;
import org.hyperledger.aries.api.credential_definition.CredentialDefinition.CredentialDefinitionsCreated;
import org.hyperledger.aries.api.credential_definition.CredentialDefinitionFilter;
import org.hyperledger.aries.api.schema.SchemaSendRequest;
import org.hyperledger.aries.api.schema.SchemaSendResponse;
import org.hyperledger.aries.api.schema.SchemasCreatedFilter;

import static org.apache.camel.component.aries.Constants.HEADER_SCHEMA_NAME;
import static org.apache.camel.component.aries.Constants.HEADER_SCHEMA_VERSION;

public class CredentialDefinitionsServiceHandler extends AbstractServiceHandler {

    public CredentialDefinitionsServiceHandler(HyperledgerAriesEndpoint endpoint) {
        super(endpoint);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void process(Exchange exchange, String service) throws Exception {

        if (service.equals("/credential-definitions/created")) {
            CredentialDefinitionFilter filter = maybeBody(exchange, CredentialDefinitionFilter.class);
            if (filter == null) {
                String schemaName = maybeHeader(exchange, HEADER_SCHEMA_NAME, String.class);
                if (schemaName == null) {
                    schemaName = getConfiguration().getSchemaName();
                }
                String schemaVersion = maybeHeader(exchange, HEADER_SCHEMA_VERSION, String.class);
                if (schemaVersion == null) {
                    schemaVersion = getConfiguration().getSchemaVersion();
                }
                filter = CredentialDefinitionFilter.builder()
                        .schemaName(schemaName)
                        .schemaVersion(schemaVersion)
                        .build();
            }
            CredentialDefinitionsCreated resObj = createClient().credentialDefinitionsCreated(filter).get();
            exchange.getIn().setBody(resObj);

        } else if (service.equals("/credential-definitions")) {
            CredentialDefinitionRequest credDefReq = maybeBody(exchange, CredentialDefinitionRequest.class);
            if (credDefReq == null) {

                Map<String, Object> spec = assertBody(exchange, Map.class);

                String schemaName = (String) spec.get("schemaName");
                if (schemaName == null) {
                    schemaName = endpoint.getConfiguration().getSchemaName();
                }
                AssertState.notNull(schemaName, "Cannot obtain schemaName");

                String schemaVersion = (String) spec.get("schemaVersion");
                if (schemaVersion == null) {
                    schemaVersion = endpoint.getConfiguration().getSchemaVersion();
                }
                AssertState.notNull(schemaVersion, "Cannot obtain schemaVersion");

                Object auxValue = spec.get("autoSchema");
                boolean autoSchema = isAutoSchema(auxValue);

                // Search existing schemas
                DID publicDid = createClient().walletDidPublic().get();
                SchemasCreatedFilter filter = SchemasCreatedFilter.builder()
                        .schemaIssuerDid(publicDid.getDid())
                        .schemaName(schemaName)
                        .schemaVersion(schemaVersion)
                        .build();
                List<String> schemaIds = createClient().schemasCreated(filter).get();

                // Create schema on-demand
                if (schemaIds.isEmpty() && autoSchema) {
                    List<String> attributes = (List<String>) spec.get("attributes");
                    SchemaSendRequest schemaReq = SchemaSendRequest.builder()
                            .schemaName(schemaName)
                            .schemaVersion(schemaVersion)
                            .attributes(attributes)
                            .build();
                    SchemaSendResponse schemaRes = createClient().schemas(schemaReq).get();
                    schemaIds = Arrays.asList(schemaRes.getSchemaId());
                    log.info("Created Schema: {}", schemaRes);
                }
                AssertState.isFalse(schemaIds.isEmpty(), "Cannot obtain schema ids for: " + filter);
                AssertState.isEqual(1, schemaIds.size(), "Unexpected number of schema ids for: " + filter);

                boolean supportRevocation = false;
                auxValue = spec.get("supportRevocation");
                if (auxValue instanceof Boolean) {
                    supportRevocation = Boolean.valueOf((Boolean) auxValue);
                }
                if (auxValue instanceof String) {
                    supportRevocation = Boolean.valueOf((String) auxValue);
                }

                credDefReq = CredentialDefinitionRequest.builder()
                        .supportRevocation(supportRevocation)
                        .schemaId(schemaIds.get(0))
                        .build();
            }
            CredentialDefinitionResponse resObj = createClient().credentialDefinitionsCreate(credDefReq).get();
            String credentialDefinitionId = resObj.getCredentialDefinitionId();
            log.info("CredentialDefinitionId: {}", credentialDefinitionId);
            exchange.getIn().setBody(resObj);
        } else {
            throw new UnsupportedServiceException(service);
        }
    }

    private boolean isAutoSchema(Object auxValue) {
        if (auxValue instanceof Boolean) {
            return Boolean.valueOf((Boolean) auxValue);
        }
        if (auxValue instanceof String) {
            return Boolean.valueOf((String) auxValue);
        }

        return endpoint.getConfiguration().isAutoSchema();
    }
}
