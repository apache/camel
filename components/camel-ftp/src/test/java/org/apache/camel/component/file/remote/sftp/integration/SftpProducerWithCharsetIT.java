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
package org.apache.camel.component.file.remote.sftp.integration;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpProducerWithCharsetIT extends SftpServerTestSupport {

    private static final String SAMPLE_FILE_NAME
            = String.format("sample-%s.txt", SftpProducerWithCharsetIT.class.getSimpleName());
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
        template.sendBodyAndHeader(getSftpUri(), SAMPLE_FILE_PAYLOAD, Exchange.FILE_NAME, SAMPLE_FILE_NAME);

        File file = new File(service.getFtpRootDir() + "/" + SAMPLE_FILE_NAME);
        assertTrue(file.exists(), "The uploaded file should exist");

        String storedPayload = FileUtils.readFileToString(file, SAMPLE_FILE_CHARSET);
        assertEquals(SAMPLE_FILE_PAYLOAD, storedPayload);
    }

    private String getSftpUri() {
        return "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?username=admin&password=admin&charset="
               + SAMPLE_FILE_CHARSET + "&knownHostsFile=" + service.getKnownHostsFile();
    }
}
