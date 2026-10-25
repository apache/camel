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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EntityUtilsTest {

    @Test
    public void testCreateEDIEntityContentTypeWithoutEncoding() throws Exception {
        ContentType ediMessageContentType = ContentType.create(AS2MediaType.APPLICATION_EDIFACT, (String) null);
        String ediMessage = "whatever";
        ApplicationEntity applicationEntity
                = EntityUtils.createEDIEntity(ediMessage.getBytes(StandardCharsets.US_ASCII), ediMessageContentType, null,
                        false, "sample.txt");
        String actualContentType = applicationEntity.getContentType();
        Assertions.assertEquals("application/edifact", actualContentType, "content type matches");
        Header[] actualContentDisposition = applicationEntity.getHeaders(AS2Header.CONTENT_DISPOSITION);
        Assertions.assertEquals(1, actualContentDisposition.length, "exactly one Content-Disposition header found");
        Assertions.assertEquals("Content-Disposition: attachment; filename=sample.txt",
                actualContentDisposition[0].toString());
    }

    @Test
    public void testCreateEDIEntityContentTypeWithEncoding() throws Exception {
        ContentType ediMessageContentType = ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII);
        String ediMessage = "whatever";
        ApplicationEntity applicationEntity
                = EntityUtils.createEDIEntity(ediMessage.getBytes(StandardCharsets.US_ASCII), ediMessageContentType, null,
                        false, "sample.txt");
        String actualContentType = applicationEntity.getContentType();
        Assertions.assertEquals("application/edifact; charset=US-ASCII", actualContentType, "content type matches");
        Header[] actualContentDisposition = applicationEntity.getHeaders(AS2Header.CONTENT_DISPOSITION);
        Assertions.assertEquals(1, actualContentDisposition.length, "exactly one Content-Disposition header found");
        Assertions.assertEquals("Content-Disposition: attachment; filename=sample.txt",
                actualContentDisposition[0].toString());
    }

    @Test
    public void testCreateEDIEntityContentTypeWithoutContentDisposition() throws Exception {
        ContentType ediMessageContentType = ContentType.create(AS2MediaType.APPLICATION_EDIFACT, (String) null);
        String ediMessage = "whatever";
        ApplicationEntity applicationEntity
                = EntityUtils.createEDIEntity(ediMessage.getBytes(StandardCharsets.US_ASCII), ediMessageContentType, null,
                        false, "");
        String actualContentType = applicationEntity.getContentType();
        Assertions.assertEquals("application/edifact", actualContentType, "content type matches");
        Header[] actualContentDisposition = applicationEntity.getHeaders(AS2Header.CONTENT_DISPOSITION);
        Assertions.assertEquals(0, actualContentDisposition.length, "no Content-Disposition headers found");
    }

    @Test
    public void testQuotedPrintableOutputStreamEncodesQuotedPrintable() throws Exception {
        byte[] message = ("UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\r\n"
                          + "FTX+AAI+++Prix = 12 \u20ac, Gr\u00fc\u00dfe, a line longer than seventy-six characters'")
                .getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (OutputStream os = EntityUtils.encode(encoded, "quoted-printable")) {
            os.write(message);
        }

        String text = encoded.toString(StandardCharsets.US_ASCII);
        Assertions.assertTrue(text.contains("Prix =3D 12 =E2=82=AC"), text);
        for (String line : text.split("\r\n")) {
            Assertions.assertTrue(line.length() <= 76, "line longer than 76 characters: " + line);
        }
        Assertions.assertArrayEquals(message, EntityUtils.decode(encoded.toByteArray(), "quoted-printable"));
    }

    @Test
    public void testQuotedPrintableInputStreamDecodesQuotedPrintable() throws Exception {
        byte[] message = "FTX+AAI+++Prix = 12 \u20ac'".getBytes(StandardCharsets.UTF_8);
        byte[] encoded = EntityUtils.encode(message, "quoted-printable");

        try (InputStream is = EntityUtils.decode(new ByteArrayInputStream(encoded), "quoted-printable")) {
            Assertions.assertArrayEquals(message, is.readAllBytes());
        }
    }
}
