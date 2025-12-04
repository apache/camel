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

package org.apache.camel.component.olingo4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchChangeRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchQueryRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchResponse;
import org.apache.camel.component.olingo4.api.batch.Operation;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.ex.ODataError;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.uri.queryoption.SystemQueryOptionKind;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("https://github.com/wiremock/wiremock/issues/3133")
public class Olingo4ComponentProducerBatchTest extends AbstractOlingo4TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Olingo4ComponentProducerBatchTest.class);

    private static final String TEST_CREATE_KEY = "'lewisblack'";
    private static final String TEST_CREATE_PEOPLE = PEOPLE + "(" + TEST_CREATE_KEY + ")";
    private static final String TEST_CREATE_RESOURCE_CONTENT_ID = "1";
    private static final String TEST_UPDATE_RESOURCE_CONTENT_ID = "2";

    @Test
    public void testBatch() throws IOException {
        final List<Olingo4BatchRequest> batchParts = new ArrayList<>();
        String resourceUri = ODATA_API_BASE_URL;
        // 1. Edm query
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(Constants.METADATA)
                .resourceUri(resourceUri)
                .headers(Map.of("Content-Disposition", "test"))
                .headers(Map.of("Content-Disposition", "test"))
                .build());

        // 2. Read entities
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(PEOPLE)
                .resourceUri(resourceUri)
                .headers(Map.of("Content-Disposition", "test"))
                .build());

        // 3. Read entity
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(TEST_PEOPLE)
                .resourceUri(resourceUri)
                .headers(Map.of("Content-Disposition", "test"))
                .build());

        // 4. Read with $top
        final HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put(SystemQueryOptionKind.TOP.toString(), "5");
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(PEOPLE)
                .resourceUri(resourceUri)
                .headers(Map.of("Content-Disposition", "test"))
                .queryParams(queryParams)
                .build());

        // 5. Create entity
        ClientEntity clientEntity = createEntity();
        batchParts.add(Olingo4BatchChangeRequest.resourcePath(PEOPLE)
                .resourceUri(resourceUri)
                .contentId(TEST_CREATE_RESOURCE_CONTENT_ID)
                .operation(Operation.CREATE)
                .body(clientEntity)
                .build());

        // 6. Update middle name in created entry
        clientEntity
                .getProperties()
                .add(objFactory.newPrimitiveProperty(
                        "MiddleName", objFactory.newPrimitiveValueBuilder().buildString("Lewis")));
        batchParts.add(Olingo4BatchChangeRequest.resourcePath(TEST_CREATE_PEOPLE)
                .resourceUri(resourceUri)
                .contentId(TEST_UPDATE_RESOURCE_CONTENT_ID)
                .operation(Operation.UPDATE)
                .body(clientEntity)
                .build());

        // 7. Delete entity
        batchParts.add(Olingo4BatchChangeRequest.resourcePath(TEST_CREATE_PEOPLE)
                .resourceUri(resourceUri)
                .operation(Operation.DELETE)
                .build());

        // 8. Read deleted entity to verify delete
        batchParts.add(Olingo4BatchQueryRequest.resourcePath(TEST_CREATE_PEOPLE)
                .resourceUri(resourceUri)
                .headers(Map.of("Content-Disposition", "test"))
                .build());

        // execute batch request
        final List<Olingo4BatchResponse> responseParts = requestBody("direct:batch", batchParts);
        assertNotNull(responseParts, "Batch response");
        assertEquals(8, responseParts.size(), "Batch responses expected");

        final Edm edm = (Edm) responseParts.get(0).getBody();
        assertNotNull(edm);
        LOG.info("Edm entity sets: {}", edm.getEntityContainer().getEntitySets());

        ClientEntitySet entitySet = (ClientEntitySet) responseParts.get(1).getBody();
        assertNotNull(entitySet);
        LOG.info("Read entities: {}", entitySet.getEntities());

        clientEntity = (ClientEntity) responseParts.get(2).getBody();
        assertNotNull(clientEntity);
        LOG.info("Read entiry properties: {}", clientEntity.getProperties());

        ClientEntitySet entitySetWithTop =
                (ClientEntitySet) responseParts.get(3).getBody();
        assertNotNull(entitySetWithTop);
        assertEquals(5, entitySetWithTop.getEntities().size());
        LOG.info("Read entities with $top=5: {}", entitySet.getEntities());

        clientEntity = (ClientEntity) responseParts.get(4).getBody();
        assertNotNull(clientEntity);
        LOG.info("Created entity: {}", clientEntity.getProperties());

        int statusCode = responseParts.get(5).getStatusCode();
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), statusCode);
        LOG.info("Update MdiddleName status: {}", statusCode);

        statusCode = responseParts.get(6).getStatusCode();
        assertEquals(HttpStatusCode.NO_CONTENT.getStatusCode(), statusCode);
        LOG.info("Delete entity status: {}", statusCode);

        assertEquals(
                HttpStatusCode.NOT_FOUND.getStatusCode(), responseParts.get(7).getStatusCode());
        final ODataError error = (ODataError) responseParts.get(7).getBody();
        assertNotNull(error);
        LOG.info("Read deleted entity error: {}", error.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for batch
                from("direct:batch").to("olingo4://batch");
            }
        };
    }
}
