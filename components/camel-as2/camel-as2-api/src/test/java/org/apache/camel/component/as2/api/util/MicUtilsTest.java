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
package org.apache.camel.component.as2.api.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.util.MicUtils.ReceivedContentMic;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.as2.api.util.MicUtils.createMic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MicUtilsTest {

    public static final Logger LOG = LoggerFactory.getLogger(MicUtilsTest.class);

    private static final String DISPOSITION_NOTIFICATION_OPTIONS_VALUE
            = " signed-receipt-protocol   =   optional  , pkcs7-signature  ;    signed-receipt-micalg   =    required  ,  sha1  ";
    private static final String CONTENT_TYPE_VALUE = AS2MimeType.APPLICATION_EDIFACT;
    private static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
                                              + "UNH+00000000000117+INVOIC:D:97B:UN'\n"
                                              + "BGM+380+342459+9'\n"
                                              + "DTM+3:20060515:102'\n"
                                              + "RFF+ON:521052'\n"
                                              + "NAD+BY+792820524::16++CUMMINS MID-RANGE ENGINE PLANT'\n"
                                              + "NAD+SE+005435656::16++GENERAL WIDGET COMPANY'\n"
                                              + "CUX+1:USD'\n"
                                              + "LIN+1++157870:IN'\n"
                                              + "IMD+F++:::WIDGET'\n"
                                              + "QTY+47:1020:EA'\n"
                                              + "ALI+US'\n"
                                              + "MOA+203:1202.58'\n"
                                              + "PRI+INV:1.179'\n"
                                              + "LIN+2++157871:IN'\n"
                                              + "IMD+F++:::DIFFERENT WIDGET'\n"
                                              + "QTY+47:20:EA'\n"
                                              + "ALI+JP'\n"
                                              + "MOA+203:410'\n"
                                              + "PRI+INV:20.5'\n"
                                              + "UNS+S'\n"
                                              + "MOA+39:2137.58'\n"
                                              + "ALC+C+ABG'\n"
                                              + "MOA+8:525'\n"
                                              + "UNT+23+00000000000117'\n"
                                              + "UNZ+1+00000000000778'";

    private static final String EDI_MESSAGE_WITH_NON_ASCII = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
                                                             + "UNH+00000000000117+INVOIC:D:97B:UN'\n"
                                                             + "BGM+380+342459+9'\n"
                                                             + "DTM+3:20060515:102'\n"
                                                             + "RFF+ON:521052'\n"
                                                             + "NAD+BY+792820524::16++CUMMINS MID-RANGE ENGINE PLANT ΠΠΠ'\n"
                                                             + "NAD+SE+005435656::16++GENERAL WIDGET COMPANY óóó'\n"
                                                             + "CUX+1:USD'\n"
                                                             + "LIN+1++157870:IN'\n"
                                                             + "IMD+F++:::WIDGET ΣΣΣ'\n"
                                                             + "QTY+47:1020:EA'\n"
                                                             + "ALI+US'\n"
                                                             + "MOA+203:1202.58'\n"
                                                             + "PRI+INV:1.179'\n"
                                                             + "LIN+2++157871:IN'\n"
                                                             + "IMD+F++:::DIFFERENT WIDGET ΦΦΦ'\n"
                                                             + "QTY+47:20:EA'\n"
                                                             + "ALI+JP'\n"
                                                             + "MOA+203:410'\n"
                                                             + "PRI+INV:20.5'\n"
                                                             + "UNS+S'\n"
                                                             + "MOA+39:2137.58'\n"
                                                             + "ALC+C+ABG'\n"
                                                             + "MOA+8:525'\n"
                                                             + "UNT+23+00000000000117'\n"
                                                             + "UNZ+1+00000000000778'";

    private static final String EXPECTED_MESSAGE_DIGEST_ALGORITHM = "sha1";
    private static final String EXPECTED_ENCODED_MESSAGE_DIGEST = "XUt+ug5GEDD0X9+Nv8DGYZZThOQ=";

    @BeforeEach
    public void setUp() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void createReceivedContentMicTest() throws Exception {

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(AS2Header.DISPOSITION_NOTIFICATION_OPTIONS, DISPOSITION_NOTIFICATION_OPTIONS_VALUE);
        request.addHeader(AS2Header.CONTENT_TYPE, CONTENT_TYPE_VALUE);

        ApplicationEDIFACTEntity edifactEntity
                = new ApplicationEDIFACTEntity(
                        EDI_MESSAGE, StandardCharsets.US_ASCII.name(), AS2TransferEncoding.NONE, true, "filename.txt");
        InputStream is = edifactEntity.getContent();
        BasicHttpEntity basicEntity = new BasicHttpEntity(is, ContentType.create(CONTENT_TYPE_VALUE));
        request.setEntity(basicEntity);

        ReceivedContentMic receivedContentMic = MicUtils.createReceivedContentMic(request, null, null);
        assertNotNull(receivedContentMic, "Failed to create Received Content MIC");
        LOG.debug("Digest Algorithm: {}", receivedContentMic.getDigestAlgorithmId());
        assertEquals(EXPECTED_MESSAGE_DIGEST_ALGORITHM, receivedContentMic.getDigestAlgorithmId(),
                "Unexpected digest algorithm value");
        LOG.debug("Encoded Message Digest: {}", receivedContentMic.getEncodedMessageDigest());
        assertEquals(EXPECTED_ENCODED_MESSAGE_DIGEST, receivedContentMic.getEncodedMessageDigest(),
                "Unexpected encoded message digest value");
    }

    // verify that a MIC is calculated correctly for an EDI message containing non ASCII chars
    @Test
    public void createReceivedContentMicWithNonAsciiContentTest() throws Exception {

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(AS2Header.DISPOSITION_NOTIFICATION_OPTIONS, DISPOSITION_NOTIFICATION_OPTIONS_VALUE);
        request.addHeader(AS2Header.CONTENT_TYPE, "application/edifact;charset=UTF-8");

        InputStream is = new ApplicationEDIFACTEntity(
                EDI_MESSAGE_WITH_NON_ASCII, StandardCharsets.UTF_8.name(), AS2TransferEncoding.NONE, true, "filename.txt")
                .getContent();
        request.setEntity(new BasicHttpEntity(is, ContentType.create(CONTENT_TYPE_VALUE, StandardCharsets.UTF_8)));
        ReceivedContentMic receivedContentMic = MicUtils.createReceivedContentMic(request, null, null);

        assertNotNull(receivedContentMic, "Failed to create Received Content MIC");
        assertEquals(EXPECTED_MESSAGE_DIGEST_ALGORITHM, receivedContentMic.getDigestAlgorithmId(),
                "Unexpected digest algorithm value");

        // calculate the MIC of the EDI message directly for comparison
        String expectedDigest = new ReceivedContentMic(
                "sha1", MicUtils.createMic(
                        // the entity parser appends 'CR' and 'LF' for each line
                        EDI_MESSAGE_WITH_NON_ASCII.replaceAll("\n", "\r\n").getBytes(StandardCharsets.UTF_8), "sha1"))
                .getEncodedMessageDigest();

        assertEquals(expectedDigest, receivedContentMic.getEncodedMessageDigest(), "Unexpected encoded message digest value");
    }

    @ParameterizedTest
    @ValueSource(strings = { "md5", "sha1", "sha256", "sha384", "sha512" })
    public void createReceivedContentMicTest(String requestedMicalg) throws Exception {
        String DNO_TEMPLATE = "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg = required, %s";

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(AS2Header.DISPOSITION_NOTIFICATION_OPTIONS, DNO_TEMPLATE.formatted(requestedMicalg));
        request.addHeader(AS2Header.CONTENT_TYPE, CONTENT_TYPE_VALUE);

        ApplicationEDIFACTEntity edifactEntity
                = new ApplicationEDIFACTEntity(
                        EDI_MESSAGE, StandardCharsets.US_ASCII.name(), AS2TransferEncoding.NONE, true, "filename.txt");
        InputStream is = edifactEntity.getContent();
        BasicHttpEntity basicEntity = new BasicHttpEntity(is, ContentType.create(CONTENT_TYPE_VALUE));
        request.setEntity(basicEntity);

        ReceivedContentMic receivedContentMic = MicUtils.createReceivedContentMic(request, null, null);
        assertNotNull(receivedContentMic, "Failed to create Received Content MIC");
        LOG.debug("Digest Algorithm: {}", receivedContentMic.getDigestAlgorithmId());
        assertEquals(requestedMicalg, receivedContentMic.getDigestAlgorithmId(),
                "Unexpected digest algorithm value");
        LOG.debug("Encoded Message Digest: {}", receivedContentMic.getEncodedMessageDigest());
        String expectedMic = getMicContent(EDI_MESSAGE, requestedMicalg);
        assertEquals(expectedMic, receivedContentMic.getEncodedMessageDigest(),
                "Unexpected encoded message digest value");
    }

    private String getMicContent(String content, String algorithm) {
        return new String(
                Base64.getEncoder().encode(
                        createMic(content
                                .replaceAll("\\n", "\r\n")
                                .getBytes(StandardCharsets.US_ASCII), algorithm)),
                StandardCharsets.US_ASCII);
    }
}
