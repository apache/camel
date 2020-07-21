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
package org.apache.camel.component.as2.api.entity;

import org.bouncycastle.cms.CMSCompressedDataGenerator;
import org.bouncycastle.cms.jcajce.ZlibCompressor;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;
import org.bouncycastle.operator.OutputCompressor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompressedEntityTest {
    
    public static final String TEXT_PLAIN_CONTENT =
            "MDN for -\r\n"
            + " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "  From: \"\\\"  as2Name  \\\"\"\r\n"
            + "  To: \"0123456780000\""
            + "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n"
            + " Status: processed\r\n"
            + " Comment: This is not a guarantee that the message has\r\n"
            + "  been completely processed or &understood by the receiving\r\n"
            + "  translator\r\n"
            + "\r\n";

    public static final String TEXT_PLAIN_CONTENT_CHARSET_NAME = "US-ASCII";

    public static final String TEXT_PLAIN_CONTENT_TRANSFER_ENCODING = "7bit";

    public static final String EXPECTED_TEXT_PLAIN_CONTENT =
            "MDN for -\r\n"
            + " Message ID: <200207310834482A70BF63@\\\"~~foo~~\\\">\r\n"
            + "  From: \"\\\"  as2Name  \\\"\"\r\n"
            + "  To: \"0123456780000\""
            + "  Received on: 2002-07-31 at 09:34:14 (EDT)\r\n"
            + " Status: processed\r\n"
            + " Comment: This is not a guarantee that the message has\r\n"
            + "  been completely processed or &understood by the receiving\r\n"
            + "  translator\r\n"
            + "\r\n";

    
    public static final String APPLICATION_PKCS7_MIME_COMPRESSED_TRANSFER_ENCODING = "base64";

    @BeforeEach
    public void setUp() throws Exception {
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    public void createCompressedEntityTest() throws Exception {
        TextPlainEntity textPlainEntity = new TextPlainEntity(TEXT_PLAIN_CONTENT, TEXT_PLAIN_CONTENT_CHARSET_NAME,
                TEXT_PLAIN_CONTENT_TRANSFER_ENCODING, false);

        CMSCompressedDataGenerator cGen = new CMSCompressedDataGenerator();

        OutputCompressor compressor = new ZlibCompressor();

        ApplicationPkcs7MimeCompressedDataEntity compressedEntity = new ApplicationPkcs7MimeCompressedDataEntity(
                textPlainEntity, cGen, compressor, APPLICATION_PKCS7_MIME_COMPRESSED_TRANSFER_ENCODING, false);

        MimeEntity decompressedEntity = compressedEntity.getCompressedEntity(new ZlibExpanderProvider());
        assertTrue(decompressedEntity instanceof TextPlainEntity, "");
        TextPlainEntity decompressedTextPlainEntity = (TextPlainEntity) decompressedEntity;
        assertEquals(EXPECTED_TEXT_PLAIN_CONTENT, decompressedTextPlainEntity.getText(), "");
    }

}
