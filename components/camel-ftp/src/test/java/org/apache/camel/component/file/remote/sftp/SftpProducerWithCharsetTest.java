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
package org.apache.camel.component.file.remote.sftp;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SftpProducerWithCharsetTest extends SftpServerTestSupport {

    private static final String SAMPLE_FILE_NAME = String.format("sample-%s.txt", SftpProducerWithCharsetTest.class.getSimpleName());
    private static final String SAMPLE_FILE_CHARSET = "iso-8859-1";
    private static final String SAMPLE_FILE_PAYLOAD = "\u00e6\u00f8\u00e5 \u00a9"; // danish
                                                                                   // ae
                                                                                   // oe
                                                                                   // aa
                                                                                   // and
                                                                                   // (c)
                                                                                   // sign

    @Test
    public void testProducerWithCharset() throws Exception {
        if (!canTest()) {
            return;
        }

        template.sendBodyAndHeader(getSftpUri(), SAMPLE_FILE_PAYLOAD, Exchange.FILE_NAME, SAMPLE_FILE_NAME);

        File file = new File(FTP_ROOT_DIR + "/" + SAMPLE_FILE_NAME);
        assertTrue(file.exists(), "The uploaded file should exist");

        String storedPayload = FileUtils.readFileToString(file, SAMPLE_FILE_CHARSET);
        assertEquals(SAMPLE_FILE_PAYLOAD, storedPayload);
    }

    private String getSftpUri() {
        return "sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "?username=admin&password=admin&charset=" + SAMPLE_FILE_CHARSET;
    }
}
