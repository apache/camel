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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SObjectCompositeResponseTest {

    @Test
    public void shouldDeserializeFailedJsonResponse() throws IOException {

        final String json = IOUtils.toString(
                this.getClass().getResourceAsStream(
                        "/org/apache/camel/component/salesforce/api/dto/composite_response_example_failure.json"),
                StandardCharsets.UTF_8);

        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final SObjectCompositeResponse response = mapper.readerFor(SObjectCompositeResponse.class).readValue(json);

        assertFailedResponse(response);
    }

    @Test
    public void shouldDeserializeSuccessfulJsonResponse() throws IOException {

        final String json = IOUtils.toString(
                this.getClass().getResourceAsStream(
                        "/org/apache/camel/component/salesforce/api/dto/composite_response_example_success.json"),
                StandardCharsets.UTF_8);

        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final SObjectCompositeResponse response = mapper.readerFor(SObjectCompositeResponse.class).readValue(json);

        assertSuccessfulResponse(response);
    }

    static void assertFailedResponse(final SObjectCompositeResponse response) {
        final List<SObjectCompositeResult> compositeResponse = response.getCompositeResponse();
        final List<SObjectCompositeResult> results = compositeResponse;
        assertThat(results).as("It should contain 2 results").hasSize(2);

        // upsert
        final SObjectCompositeResult upsertResponse = compositeResponse.get(0);
        assertThat(upsertResponse.getReferenceId()).as("ReferenceId of first operation should be NewPayment1")
                .isEqualTo("NewPayment1");
        assertThat(upsertResponse.getHttpStatusCode()).as("httpStatusCode of first operation should be 400").isEqualTo(400);
        assertThat(upsertResponse.getBody()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> upsertBody = (List<Map<String, Object>>) upsertResponse.getBody();
        assertThat(upsertBody).hasSize(1);
        final Map<String, Object> upsertBodyContent = upsertBody.get(0);
        assertThat(upsertBodyContent).as("message of the create operation should be populated properly")
                .containsEntry("message",
                        "The transaction was rolled back since another operation in the same transaction failed.");
        assertThat(upsertBodyContent).as("errorCode of the create operation should be PROCESSING_HALTED")
                .containsEntry("errorCode", "PROCESSING_HALTED");

        // create
        final SObjectCompositeResult createResponse = compositeResponse.get(1);
        assertThat(createResponse.getReferenceId()).as("ReferenceId of first operation should be NewPayment2")
                .isEqualTo("NewPayment2");
        assertThat(createResponse.getHttpStatusCode()).as("httpStatusCode of first operation should be 400").isEqualTo(400);
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> createBody = (List<Map<String, Object>>) createResponse.getBody();
        assertThat(createBody).hasSize(1);
        final Map<String, Object> createBodyContent = createBody.get(0);
        assertThat(createBodyContent).as("message of the create operation should be populated properly")
                .containsEntry("message",
                        "Foreign key external ID: 0116 not found for field Invoice_External_Id__c in entity blng__Invoice__c");
        assertThat(createBodyContent).as("errorCode of the create operation should be INVALID_FIELD").containsEntry("errorCode",
                "INVALID_FIELD");
    }

    static void assertSuccessfulResponse(final SObjectCompositeResponse response) {

        final List<SObjectCompositeResult> compositeResponse = response.getCompositeResponse();
        final List<SObjectCompositeResult> results = compositeResponse;
        assertThat(results).as("It should contain 2 results").hasSize(2);

        // create 1
        final SObjectCompositeResult firstResponse = compositeResponse.get(0);
        assertThat(firstResponse.getHttpHeaders()).as("Location of the create resource should be populated")
                .containsEntry("Location", "/services/data/v41.0/sobjects/blng__Payment__c/a1V3E000000EXomUAM");
        assertThat(firstResponse.getHttpStatusCode()).as("httpStatusCode of the create operation should be 201").isEqualTo(201);
        assertThat(firstResponse.getReferenceId()).as("ReferenceId of the create operation should be NewPayment1")
                .isEqualTo("NewPayment1");
        @SuppressWarnings("unchecked")
        final Map<String, Object> firstResponseMap = (Map<String, Object>) firstResponse.getBody();
        assertThat(firstResponseMap).as("id of the create operation should be a1V3E000000EXomUAM").containsEntry("id",
                "a1V3E000000EXomUAM");
        assertThat(firstResponseMap).as("success of the create operation should be true").containsEntry("success",
                Boolean.TRUE);

        // create 2
        final SObjectCompositeResult secondResponse = compositeResponse.get(1);
        assertThat(secondResponse.getHttpHeaders()).as("Location of the create resource should be populated")
                .containsEntry("Location", "/services/data/v41.0/sobjects/blng__Payment__c/a1V3E000000EXomUAG");
        assertThat(secondResponse.getHttpStatusCode()).as("httpStatusCode of the create operation should be 201")
                .isEqualTo(201);
        assertThat(secondResponse.getReferenceId()).as("ReferenceId of the create operation should be NewPayment2")
                .isEqualTo("NewPayment2");

        @SuppressWarnings("unchecked")
        final Map<String, Object> secondResponseMap = (Map<String, Object>) secondResponse.getBody();
        assertThat(secondResponseMap).as("id of the create operation should be a1V3E000000EXomUAG").containsEntry("id",
                "a1V3E000000EXomUAG");
        assertThat(secondResponseMap).as("success of the create operation should be true").containsEntry("success",
                Boolean.TRUE);
    }

}
