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
package org.apache.camel.component.google.sheets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiCollection;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiName;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.component.ApiMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer and the consumer execute the request that the method of the API builds. The accessors of the
 * sub-resources of Sheets.Spreadsheets, such as values(), build no request, so they must not be methods of the
 * component.
 */
class GoogleSheetsApiMethodsTest {

    private static final String OAUTH = "clientId=id&clientSecret=secret&refreshToken=token";

    @Test
    void shouldOnlyHaveMethodsThatBuildARequest() {
        List<String> notRequests = new ArrayList<>();
        for (Map.Entry<Class<? extends ApiMethod>, GoogleSheetsApiName> api : GoogleSheetsApiCollection.getCollection()
                .getApiMethods().entrySet()) {
            for (ApiMethod method : api.getKey().getEnumConstants()) {
                if (!AbstractGoogleClientRequest.class.isAssignableFrom(method.getResultType())) {
                    notRequests.add(api.getValue().getName() + "/" + method.getName());
                }
            }
        }

        assertEquals(List.of(), notRequests, "These methods build no request");
    }

    @Test
    void shouldListTheMethodNamesOfTheApisInTheMethodNameOption() throws Exception {
        Set<String> methodNames = new TreeSet<>();
        for (Class<? extends ApiMethod> api : GoogleSheetsApiCollection.getCollection().getApiMethods().keySet()) {
            for (ApiMethod method : api.getEnumConstants()) {
                methodNames.add(method.getName());
            }
        }
        String enums = GoogleSheetsConfiguration.class.getDeclaredField("methodName").getAnnotation(UriPath.class).enums();

        assertEquals(methodNames, new TreeSet<>(Arrays.asList(enums.split(","))));
    }

    @ParameterizedTest
    @ValueSource(strings = { "developerMetadata", "sheets", "values" })
    void shouldRejectTheSubResourceAccessorsOfSpreadsheets(String accessor) throws Exception {
        try (CamelContext context = createContext(new MockLowLevelHttpResponse())) {
            // the accessors take no argument, so no option is given that would rule them out
            ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                    () -> context.getEndpoint("google-sheets://spreadsheets/" + accessor + "?" + OAUTH));

            assertTrue(e.getMessage().contains("No matching method for spreadsheets/" + accessor), e.getMessage());
        }
    }

    @Test
    void shouldReadTheValuesWithTheDataApi() throws Exception {
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse().setContentType(Json.MEDIA_TYPE)
                .setContent(
                        "{\"range\":\"Sheet1!A1:B2\",\"majorDimension\":\"ROWS\",\"values\":[[\"a\",\"b\"],[\"c\",\"d\"]]}");
        try (CamelContext context = createContext(response)) {
            ValueRange body = context.createProducerTemplate().requestBody(
                    "google-sheets://data/get?spreadsheetId=abc&range=Sheet1!A1:B2&" + OAUTH, null, ValueRange.class);

            assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), body.getValues());
        }
    }

    private static CamelContext createContext(MockLowLevelHttpResponse response) {
        CamelContext context = new DefaultCamelContext();
        context.getComponent("google-sheets", GoogleSheetsComponent.class)
                .setClientFactory(new MockGoogleSheetsClientFactory(response));
        context.start();
        return context;
    }
}
