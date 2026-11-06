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

package org.apache.camel.test.infra.azure.eventgrid.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.UUID;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.azure.common.services.FlociAzContainer;
import org.apache.camel.test.infra.azure.common.services.FlociAzInfraService;
import org.apache.camel.test.infra.azure.eventgrid.clients.AzureEventGridManagementClient;
import org.apache.camel.test.infra.azure.eventgrid.common.AzureEventGridProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A local Event Grid service: a custom topic of Floci AZ that takes events in the CloudEvents 1.0 schema. The Event
 * Grid SDK sends an access key only over HTTPS, so Floci AZ serves HTTPS too, on the same port, with a certificate
 * signed by a certificate authority of its own: {@link #trustStore()} holds that authority.
 */
@InfraService(service = AzureEventGridInfraService.class,
              description = "Local Azure Event Grid service with Floci AZ",
              serviceAlias = "azure", serviceImplementationAlias = "event-grid")
public class AzureEventGridLocalContainerInfraService extends FlociAzInfraService implements AzureEventGridInfraService {
    private static final Logger LOG = LoggerFactory.getLogger(AzureEventGridLocalContainerInfraService.class);
    private static final String TOPIC_NAME = "camel-topic";
    private static final String TOPIC_ID = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/camel"
                                           + "/providers/Microsoft.EventGrid/topics/" + TOPIC_NAME;

    private final String trustStorePassword = UUID.randomUUID().toString();
    private String topicAccessKey;
    private Path trustStore;

    @Override
    protected FlociAzContainer initContainer(String imageName) {
        return super.initContainer(imageName).withEnv("FLOCI_AZ_TLS_ENABLED", "true");
    }

    @Override
    public void initialize() {
        getContainer().start();

        AzureEventGridManagementClient client = new AzureEventGridManagementClient(resourceManagerEndpoint());
        client.createCloudEventTopic(TOPIC_ID);
        topicAccessKey = client.topicAccessKey(TOPIC_ID);
        trustStore = createTrustStore(client.tlsCertificate());

        LOG.info("Floci AZ Event Grid topic {} running at {}", TOPIC_NAME, topicEndpoint());

        registerProperties();
    }

    /**
     * Registers the properties of the topic, and not those of the storage account of Floci AZ, which Event Grid does
     * not use
     */
    @Override
    public void registerProperties() {
        System.setProperty(AzureEventGridProperties.TOPIC_ENDPOINT, topicEndpoint());
        System.setProperty(AzureEventGridProperties.ACCESS_KEY, topicAccessKey());
    }

    @Override
    public void shutdown() {
        super.shutdown();

        if (trustStore != null) {
            try {
                Files.deleteIfExists(trustStore);
            } catch (IOException e) {
                LOG.warn("Cannot delete the trust store {}: {}", trustStore, e.getMessage());
            }
        }
    }

    /**
     * The endpoint of the topic at the address of the container: the endpoint Floci AZ gives for the topic is built
     * from its own base URL ({@code http://localhost:4577}), which is not the mapped port of the container.
     */
    @Override
    public String topicEndpoint() {
        return String.format("https://%s:%d/%s-eventgrid/api/events", host(), port(), TOPIC_NAME);
    }

    @Override
    public String topicAccessKey() {
        return topicAccessKey;
    }

    @Override
    public String topicId() {
        return TOPIC_ID;
    }

    @Override
    public String resourceManagerEndpoint() {
        return getContainer().serviceAddress();
    }

    @Override
    public String trustStore() {
        return trustStore != null ? trustStore.toString() : null;
    }

    @Override
    public String trustStorePassword() {
        return trustStore != null ? trustStorePassword : null;
    }

    private Path createTrustStore(String certificateAuthority) {
        try {
            Certificate certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certificateAuthority.getBytes(StandardCharsets.US_ASCII)));

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setCertificateEntry("floci-az", certificate);

            Path file = Files.createTempFile("floci-az-", ".p12");
            try (OutputStream out = Files.newOutputStream(file)) {
                keyStore.store(out, trustStorePassword.toCharArray());
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot create a trust store with the certificate authority of Floci AZ", e);
        }
    }
}
