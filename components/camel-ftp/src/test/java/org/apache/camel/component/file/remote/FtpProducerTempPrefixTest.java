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

import java.io.File;

import org.apache.camel.converter.IOConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test to verify that Camel can build remote directory on FTP server if
 * missing (full or part of).
 */
public class FtpProducerTempPrefixTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/upload/user/claus?binary=false&password=admin&tempPrefix=.uploading";
    }

    @Test
    public void testProduceTempPrefixTest() throws Exception {
        sendFile(getFtpUrl(), "Hello World", "claus.txt");

        File file = new File(FTP_ROOT_DIR + "/upload/user/claus/claus.txt");
        assertTrue(file.exists(), "The uploaded file should exists");
        assertEquals("Hello World", IOConverter.toString(file, null));
    }
}
