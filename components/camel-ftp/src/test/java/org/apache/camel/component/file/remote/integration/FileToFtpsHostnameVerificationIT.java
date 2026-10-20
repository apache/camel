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
package org.apache.camel.component.file.remote.integration;

import javax.net.ssl.SSLHandshakeException;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.apache.camel.test.junit6.TestSupport.assertFileExists;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test that the ftps component checks the host name of the server: the certificate of the test server only lists
 * localhost, not 127.0.0.1.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.FtpsUtil#hasRequiredAlgorithms")
public class FileToFtpsHostnameVerificationIT extends FtpsServerExplicitSSLWithoutClientAuthTestSupport {

    @BindToRegistry("sslContextParameters")
    public SSLContextParameters createSslContextParams() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("server.jks");
        ksp.setPassword("password");

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setSecureSocketProtocol("TLSv1.2");
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }

    private String getFtpUrl(String host, String directory) {
        return "ftps://admin@" + host + ":" + service.getPort() + "/" + directory
               + "?password=admin&disableSecureDataChannelDefaults=true&implicit=false"
               + "&sslContextParameters=#sslContextParameters&maximumReconnectAttempts=0&fileName=hello.txt";
    }

    @Test
    public void uploadToHostListedInCertificate() {
        template.sendBody(getFtpUrl("localhost", "listed"), "Hello World");

        assertFileExists(service.ftpFile("listed/hello.txt"));
    }

    @Test
    public void uploadToHostNotListedInCertificateFails() {
        String uri = getFtpUrl("127.0.0.1", "notlisted");

        Exception ex = assertThrows(CamelExecutionException.class, () -> template.sendBody(uri, "Hello World"));

        assertNotNull(ObjectHelper.getException(SSLHandshakeException.class, ex), "The TLS handshake should fail");
        assertFalse(service.ftpFile("notlisted/hello.txt").toFile().exists());
    }

    @Test
    public void uploadToHostNotListedInCertificateWithHostnameVerificationTurnedOff() {
        template.sendBody(getFtpUrl("127.0.0.1", "unchecked") + "&hostnameVerification=false", "Hello World");

        assertFileExists(service.ftpFile("unchecked/hello.txt"));
    }
}
