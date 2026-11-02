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
package org.apache.camel.component.google.mail;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.services.gmail.model.MessagePartBody;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.google.mail.internal.GoogleMailApiCollection;
import org.apache.camel.component.google.mail.internal.GoogleMailApiName;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.component.ApiMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer and the consumer execute the request that the method of the API builds. An accessor of a sub-resource,
 * such as Gmail.Users.Messages.attachments(), builds no request, so it must not be a method of the component.
 */
class GoogleMailApiMethodsTest {

    private static final String OAUTH = "clientId=id&clientSecret=secret&refreshToken=token";

    @Test
    void shouldOnlyHaveMethodsThatBuildARequest() {
        List<String> notRequests = new ArrayList<>();
        for (Map.Entry<Class<? extends ApiMethod>, GoogleMailApiName> api : GoogleMailApiCollection.getCollection()
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
        for (Class<? extends ApiMethod> api : GoogleMailApiCollection.getCollection().getApiMethods().keySet()) {
            for (ApiMethod method : api.getEnumConstants()) {
                methodNames.add(method.getName());
            }
        }
        String enums = GoogleMailConfiguration.class.getDeclaredField("methodName").getAnnotation(UriPath.class).enums();

        assertEquals(methodNames, new TreeSet<>(Arrays.asList(enums.split(","))));
    }

    @Test
    void shouldRejectTheAttachmentsAccessorOfMessages() throws Exception {
        try (CamelContext context = createContext(new MockGoogleMailClientFactory("{}"))) {
            ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                    () -> context.getEndpoint("google-mail://messages/attachments?" + OAUTH));

            assertTrue(e.getMessage().contains("No matching method for messages/attachments"), e.getMessage());
        }
    }

    @Test
    void shouldGetAnAttachmentWithTheAttachmentsApi() throws Exception {
        String data = Base64.getUrlEncoder().encodeToString("Hello".getBytes(StandardCharsets.UTF_8));
        MockGoogleMailClientFactory factory
                = new MockGoogleMailClientFactory("{\"attachmentId\":\"a1\",\"size\":5,\"data\":\"" + data + "\"}");
        try (CamelContext context = createContext(factory)) {
            ProducerTemplate template = context.createProducerTemplate();

            MessagePartBody body = template.requestBody(
                    "google-mail://attachments/get?userId=me&messageId=m1&id=a1&" + OAUTH, null, MessagePartBody.class);

            assertEquals("Hello", new String(body.decodeData(), StandardCharsets.UTF_8));
            List<String> urls = factory.getRequestUrls();
            assertTrue(urls.stream().anyMatch(url -> url.endsWith("/users/me/messages/m1/attachments/a1")),
                    "The attachment should be read from its message: " + urls);
        }
    }

    private static CamelContext createContext(GoogleMailClientFactory factory) {
        CamelContext context = new DefaultCamelContext();
        context.getComponent("google-mail", GoogleMailComponent.class).setClientFactory(factory);
        context.start();
        return context;
    }
}
