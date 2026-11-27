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
package org.apache.camel.component.solr;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Collections;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SSL options of the client that Camel creates. The CamelContext is not started in most tests, so that looking up
 * the endpoint does not start it, and the test calls {@link SolrEndpoint#createSolrClient()}, which the endpoint calls
 * at startup.
 */
public class SolrSslConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    public void certificatePathWithoutEnableSslFails() throws Exception {
        String certificatePath = "file:" + writeCaCertificate();
        try (CamelContext context = new DefaultCamelContext()) {
            SolrEndpoint endpoint = context.getEndpoint("solr://localhost:8983?certificatePath=" + certificatePath,
                    SolrEndpoint.class);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, endpoint::createSolrClient);
            assertTrue(e.getMessage().contains("enableSSL=true"), e.getMessage());
        }
    }

    @Test
    public void sslContextParametersWithoutEnableSslFails() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            SolrComponent component = new SolrComponent(context);
            component.setSslContextParameters(new SSLContextParameters());
            context.addComponent("solr", component);

            SolrEndpoint endpoint = context.getEndpoint("solr://localhost:8983", SolrEndpoint.class);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, endpoint::createSolrClient);
            assertTrue(e.getMessage().contains("enableSSL=true"), e.getMessage());
        }
    }

    @Test
    public void routeWithCertificatePathWithoutEnableSslFailsAtStartup() throws Exception {
        String certificatePath = "file:" + writeCaCertificate();
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("solr://localhost:8983?certificatePath=" + certificatePath);
                }
            });

            Exception e = assertThrows(Exception.class, context::start);
            IllegalArgumentException cause = ObjectHelper.getException(IllegalArgumentException.class, e);
            assertNotNull(cause, "Expected an IllegalArgumentException as cause of " + e);
            assertTrue(cause.getMessage().contains("enableSSL=true"), cause.getMessage());
        }
    }

    @Test
    public void globalSslContextParametersWithoutEnableSslUsePlainHttp() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.setSSLContextParameters(new SSLContextParameters());
            SolrComponent component = new SolrComponent(context);
            component.setUseGlobalSslContextParameters(true);
            context.addComponent("solr", component);

            SolrEndpoint endpoint = context.getEndpoint("solr://localhost:8983", SolrEndpoint.class);
            try (SolrClient client = endpoint.createSolrClient()) {
                HttpJdkSolrClient httpClient = assertInstanceOf(HttpJdkSolrClient.class, client);
                assertEquals("http://localhost:8983/solr", httpClient.getBaseURL());
            }
        }
    }

    @Test
    public void certificatePathWithEnableSslUsesHttps() throws Exception {
        String certificatePath = "file:" + writeCaCertificate();
        try (CamelContext context = new DefaultCamelContext()) {
            SolrEndpoint endpoint = context.getEndpoint(
                    "solr://localhost:8983?enableSSL=true&certificatePath=" + certificatePath, SolrEndpoint.class);
            try (SolrClient client = endpoint.createSolrClient()) {
                HttpJdkSolrClient httpClient = assertInstanceOf(HttpJdkSolrClient.class, client);
                assertEquals("https://localhost:8983/solr", httpClient.getBaseURL());
            }
        }
    }

    @Test
    public void certificatePathWithHttpUrlFails() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            for (String certificatePath : new String[] { "http://localhost:8080/ca.crt", "HTTP://localhost:8080/ca.crt" }) {
                SolrEndpoint endpoint = context.getEndpoint(
                        "solr://localhost:8983?enableSSL=true&certificatePath=" + certificatePath, SolrEndpoint.class);
                IllegalArgumentException e = assertThrows(IllegalArgumentException.class, endpoint::createSolrClient);
                assertTrue(e.getMessage().contains("http: URL"), e.getMessage());
            }
        }
    }

    /**
     * Writes the certificate of the test key store as a DER file, to use as the CA certificate of the client.
     */
    private Path writeCaCertificate() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream is = SolrSslConfigurationTest.class.getResourceAsStream("/solrtest.keystore")) {
            keyStore.load(is, "secret".toCharArray());
        }
        String alias = Collections.list(keyStore.aliases()).get(0);
        Path file = tempDir.resolve("ca.der");
        Files.write(file, keyStore.getCertificate(alias).getEncoded());
        return file;
    }
}
