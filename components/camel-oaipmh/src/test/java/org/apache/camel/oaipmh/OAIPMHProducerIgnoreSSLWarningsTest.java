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
package org.apache.camel.oaipmh;

import java.util.List;

import javax.net.ssl.SSLException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.oaipmh.utils.MockOaipmhServer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The ignoreSSLWarnings endpoint option is the only setting that makes the producer accept a self-signed certificate;
 * the CamelOaimphIgnoreSSLWarnings message header is not read.
 */
class OAIPMHProducerIgnoreSSLWarningsTest extends CamelTestSupport {

    private static final String IGNORE_SSL_WARNINGS_HEADER = "CamelOaimphIgnoreSSLWarnings";

    private static MockOaipmhServer mockOaipmhServer;

    @BeforeAll
    static void startServer() {
        mockOaipmhServer = MockOaipmhServer.create();
        mockOaipmhServer.start();
    }

    @AfterAll
    static void stopServer() {
        mockOaipmhServer.stop();
    }

    @Test
    void ignoreSSLWarningsHeaderDoesNotChangeTheCertificateCheck() {
        String uri = "oaipmh://localhost:" + mockOaipmhServer.getHttpsPort() + "/oai/request?ssl=true&verb=Identify";

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader(uri, null, IGNORE_SSL_WARNINGS_HEADER, true, List.class),
                "the self-signed certificate is rejected when the endpoint option is not set");
        assertInstanceOf(SSLException.class, e.getCause(), "cause: " + e.getCause());
    }

    @Test
    void ignoreSSLWarningsEndpointOptionAcceptsTheSelfSignedCertificate() {
        String uri = "oaipmh://localhost:" + mockOaipmhServer.getHttpsPort()
                     + "/oai/request?ssl=true&verb=Identify&ignoreSSLWarnings=true";

        List<?> identify = template.requestBody(uri, null, List.class);
        assertEquals(1, identify.size());

        List<?> identifyWithHeader = template.requestBodyAndHeader(uri, null, IGNORE_SSL_WARNINGS_HEADER, false, List.class);
        assertEquals(1, identifyWithHeader.size(), "the header does not change the endpoint option");
    }
}
