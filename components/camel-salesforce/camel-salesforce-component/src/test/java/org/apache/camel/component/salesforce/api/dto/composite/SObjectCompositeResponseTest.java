/**
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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SObjectCompositeResponseTest {

	static void assertSuccessfulResponse(final SObjectCompositeResponse response) {

		final List<SObjectCompositeResult> results = response.getCompositeResponse();
		assertEquals("It should contain 2 results", 2, results.size());
		
		// upsert
		assertEquals("ReferenceId of first operation should be UpsertPayment", "UpsertPayment", 
				response.getCompositeResponse().get(0).getReferenceId());
		assertEquals("httpStatusCode of first operation should be 204", 204, 
				response.getCompositeResponse().get(0).getHttpStatusCode());
		// create
		assertEquals("Location of the create resource should be populated", "/services/data/v41.0/sobjects/blng__PaymentAllocationInvoice__c/a1V3E000000EXomUAG", 
				response.getCompositeResponse().get(1).getHttpHeaders().get("Location"));
		assertEquals("httpStatusCode of the create operation should be 201", 201, 
				response.getCompositeResponse().get(1).getHttpStatusCode());
		assertEquals("ReferenceId of the create operation should be NewPaymentAllocation", "NewPaymentAllocation", 
				response.getCompositeResponse().get(1).getReferenceId());
		
		assertEquals("id of the create operation should be a1V3E000000EXomUAG", "a1V3E000000EXomUAG", 
				((HashMap<?, ?>)response.getCompositeResponse().get(1).getBody()).get("id"));
		assertEquals("success of the create operation should be true", true, 
				((HashMap<?, ?>)response.getCompositeResponse().get(1).getBody()).get("success"));
	}

	static void assertFailedResponse(final SObjectCompositeResponse response) {
		// upsert
		assertEquals("ReferenceId of first operation should be UpsertPayment", "UpsertPayment", 
				response.getCompositeResponse().get(0).getReferenceId());
		assertEquals("httpStatusCode of first operation should be 400", 400, 
				response.getCompositeResponse().get(0).getHttpStatusCode());
		assertEquals("message of the create operation should be populated properly", "The transaction was rolled back since another operation in the same transaction failed.", 
				((HashMap<?, ?>)((List<?>)response.getCompositeResponse().get(0).getBody()).get(0)).get("message"));
		assertEquals("errorCode of the create operation should be PROCESSING_HALTED", "PROCESSING_HALTED", 
				((HashMap<?, ?>)((List<?>)response.getCompositeResponse().get(0).getBody()).get(0)).get("errorCode"));
		
		// create
		assertEquals("ReferenceId of first operation should be NewPaymentAllocation", "NewPaymentAllocation", 
				response.getCompositeResponse().get(1).getReferenceId());
		assertEquals("httpStatusCode of first operation should be 400", 400, 
				response.getCompositeResponse().get(1).getHttpStatusCode());
		assertEquals("message of the create operation should be populated properly", "Foreign key external ID: 1210003862 not found for field MQ2C_Payment_External_Id__c in entity blng__Payment__c", 
				((HashMap<?, ?>)((List<?>)response.getCompositeResponse().get(1).getBody()).get(0)).get("message"));
		assertEquals("errorCode of the create operation should be INVALID_FIELD", "INVALID_FIELD", 
				((HashMap<?, ?>)((List<?>)response.getCompositeResponse().get(1).getBody()).get(0)).get("errorCode"));
	}

	@Test
	public void shouldDeserializeSuccessfulJsonResponse() throws IOException {

		String json = IOUtils.toString(
				this.getClass().getResourceAsStream("/org/apache/camel/component/salesforce/api/dto/composite_response_example_success.json"),
				Charset.forName("UTF-8"));

		final ObjectMapper mapper = new ObjectMapper();

		final SObjectCompositeResponse response = mapper.readerFor(SObjectCompositeResponse.class).readValue(json);

		assertSuccessfulResponse(response);
	}

	@Test
	public void shouldDeserializeFailedJsonResponse() throws IOException {

		String json = IOUtils.toString(
				this.getClass().getResourceAsStream("/org/apache/camel/component/salesforce/api/dto/composite_response_example_failure.json"),
				Charset.forName("UTF-8"));

		final ObjectMapper mapper = new ObjectMapper();

		final SObjectCompositeResponse response = mapper.readerFor(SObjectCompositeResponse.class).readValue(json);

		assertFailedResponse(response);
	}

}
