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
package org.apache.camel.component.file.remote;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.TrustManagerUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The trust manager of the FTPS client that the endpoint creates. The certificate of the test server is self-signed and
 * only in src/test/resources/server.jks.
 */
public class FtpsEndpointTrustManagerTest extends CamelTestSupport {

    private static final String TRUST_STORE_OPTIONS
            = "ftpClient.trustStore.file=./src/test/resources/server.jks&ftpClient.trustStore.type=JKS"
              + "&ftpClient.trustStore.password=password";

    @BindToRegistry("customFtpClient")
    private final FTPSClient customFtpClient = new FTPSClient();

    @Test
    public void createdClientWithoutTrustSettingsUsesJvmDefaultTrustStore() throws Exception {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://localhost/dir", FtpsEndpoint.class);

        FTPSClient client = (FTPSClient) endpoint.createFtpClient();

        assertEquals(TrustManagerUtils.getDefaultTrustManager(null).getClass(), client.getTrustManager().getClass());
    }

    @Test
    public void createdClientWithoutTrustSettingsRejectsServerCertificateNotInTrustStore() throws Exception {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://localhost/dir", FtpsEndpoint.class);
        X509Certificate[] chain = serverCertificateChain();

        X509TrustManager trustManager
                = assertInstanceOf(X509TrustManager.class, ((FTPSClient) endpoint.createFtpClient()).getTrustManager());

        assertThrows(CertificateException.class, () -> trustManager.checkServerTrusted(chain, "RSA"));
    }

    @Test
    public void createdClientWithTrustStoreOptionsAcceptsServerCertificateInTrustStore() throws Exception {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://localhost/dir?" + TRUST_STORE_OPTIONS, FtpsEndpoint.class);
        X509Certificate[] chain = serverCertificateChain();

        X509TrustManager trustManager
                = assertInstanceOf(X509TrustManager.class, ((FTPSClient) endpoint.createFtpClient()).getTrustManager());

        assertDoesNotThrow(() -> trustManager.checkServerTrusted(chain, "RSA"));
    }

    @Test
    public void customFtpClientKeepsItsTrustManager() throws Exception {
        X509TrustManager before = assertInstanceOf(X509TrustManager.class, customFtpClient.getTrustManager());
        FtpsEndpoint endpoint = context.getEndpoint("ftps://localhost/dir?ftpClient=#customFtpClient", FtpsEndpoint.class);

        endpoint.createRemoteFileOperations();

        assertSame(before, customFtpClient.getTrustManager());
    }

    private static X509Certificate[] serverCertificateChain() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream is = Files.newInputStream(Paths.get("src/test/resources/server.jks"))) {
            keyStore.load(is, "password".toCharArray());
        }
        return new X509Certificate[] { (X509Certificate) keyStore.getCertificate("ftpserver") };
    }
}
