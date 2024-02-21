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

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.util.MicUtils.ReceivedContentMic;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final String EXPECTED_MESSAGE_DIGEST_ALGORITHM = "sha1";
    private static final String EXPECTED_ENCODED_MESSAGE_DIGEST = "XUt+ug5GEDD0X9+Nv8DGYZZThOQ=";

    @BeforeEach
    public void setUp() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    public void createReceivedContentMicTest() throws Exception {

        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/", HttpVersion.HTTP_1_1);
        request.addHeader(AS2Header.DISPOSITION_NOTIFICATION_OPTIONS, DISPOSITION_NOTIFICATION_OPTIONS_VALUE);
        request.addHeader(AS2Header.CONTENT_TYPE, CONTENT_TYPE_VALUE);

        ApplicationEDIFACTEntity edifactEntity
                = new ApplicationEDIFACTEntity(
                        EDI_MESSAGE, StandardCharsets.US_ASCII.name(), AS2TransferEncoding.NONE, true, "filename.txt");
        InputStream is = edifactEntity.getContent();
        BasicHttpEntity basicEntity = new BasicHttpEntity();
        basicEntity.setContent(is);
        basicEntity.setContentType(CONTENT_TYPE_VALUE);
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

}
