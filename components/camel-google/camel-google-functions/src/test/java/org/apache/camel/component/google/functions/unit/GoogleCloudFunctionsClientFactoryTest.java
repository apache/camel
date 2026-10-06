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
package org.apache.camel.component.google.functions.unit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient;
import com.google.cloud.functions.v1.CloudFunctionsServiceSettings;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsClientFactory;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class GoogleCloudFunctionsClientFactoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesApplicationDefaultCredentialsWhenServiceAccountKeyIsAbsent() throws Exception {
        GoogleCredentials credentials = mock(GoogleCredentials.class);
        CloudFunctionsServiceClient client = mock(CloudFunctionsServiceClient.class);
        try (DefaultCamelContext context = new DefaultCamelContext();
             MockedStatic<GoogleCredentials> adc = mockStatic(GoogleCredentials.class);
             MockedStatic<CloudFunctionsServiceClient> clients = mockStatic(CloudFunctionsServiceClient.class)) {
            adc.when(GoogleCredentials::getApplicationDefault).thenReturn(credentials);
            stubClientCreation(clients, credentials, client);

            assertSame(client, GoogleCloudFunctionsClientFactory.create(context, new GoogleCloudFunctionsConfiguration()));

            adc.verify(GoogleCredentials::getApplicationDefault);
            clients.verify(() -> CloudFunctionsServiceClient.create(any(CloudFunctionsServiceSettings.class)));
        }
    }

    @Test
    void serviceAccountKeyTakesPrecedenceOverApplicationDefaultCredentials() throws Exception {
        Path keyFile = temporaryDirectory.resolve("service-account.json");
        Files.writeString(keyFile, "service-account-key");
        GoogleCloudFunctionsConfiguration configuration = new GoogleCloudFunctionsConfiguration();
        configuration.setServiceAccountKey(keyFile.toUri().toString());
        ServiceAccountCredentials credentials = mock(ServiceAccountCredentials.class);
        CloudFunctionsServiceClient client = mock(CloudFunctionsServiceClient.class);
        try (DefaultCamelContext context = new DefaultCamelContext();
             MockedStatic<GoogleCredentials> adc = mockStatic(GoogleCredentials.class);
             MockedStatic<ServiceAccountCredentials> keys = mockStatic(ServiceAccountCredentials.class);
             MockedStatic<CloudFunctionsServiceClient> clients = mockStatic(CloudFunctionsServiceClient.class)) {
            keys.when(() -> ServiceAccountCredentials.fromStream(any(InputStream.class))).thenAnswer(invocation -> {
                InputStream stream = invocation.getArgument(0);
                assertEquals("service-account-key", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                return credentials;
            });
            stubClientCreation(clients, credentials, client);

            assertSame(client, GoogleCloudFunctionsClientFactory.create(context, configuration));

            keys.verify(() -> ServiceAccountCredentials.fromStream(any(InputStream.class)));
            adc.verifyNoInteractions();
            clients.verify(() -> CloudFunctionsServiceClient.create(any(CloudFunctionsServiceSettings.class)));
        }
    }

    @Test
    void propagatesApplicationDefaultCredentialFailureWithoutCreatingClient() throws Exception {
        IOException failure = new IOException("Application Default Credentials unavailable");
        try (DefaultCamelContext context = new DefaultCamelContext();
             MockedStatic<GoogleCredentials> adc = mockStatic(GoogleCredentials.class);
             MockedStatic<CloudFunctionsServiceClient> clients = mockStatic(CloudFunctionsServiceClient.class)) {
            adc.when(GoogleCredentials::getApplicationDefault).thenThrow(failure);

            assertSame(failure, assertThrows(IOException.class,
                    () -> GoogleCloudFunctionsClientFactory.create(context, new GoogleCloudFunctionsConfiguration())));

            clients.verifyNoInteractions();
        }
    }

    private void stubClientCreation(
            MockedStatic<CloudFunctionsServiceClient> clients, Credentials credentials, CloudFunctionsServiceClient client) {
        clients.when(() -> CloudFunctionsServiceClient.create(any(CloudFunctionsServiceSettings.class)))
                .thenAnswer(invocation -> {
                    CloudFunctionsServiceSettings settings = invocation.getArgument(0);
                    assertSame(credentials, settings.getCredentialsProvider().getCredentials());
                    return client;
                });
    }
}
