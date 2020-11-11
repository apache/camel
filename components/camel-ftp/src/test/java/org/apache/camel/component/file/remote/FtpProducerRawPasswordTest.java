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
 * Unit test for password parameter using RAW value
 */
public class FtpProducerRawPasswordTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        // START SNIPPET: e1
        // notice how we use RAW(value) to tell Camel that the password field is
        // a RAW value and should not be
        // uri encoded. This allows us to use the password 'as is' containing +
        // & and other signs
        return "ftp://joe@localhost:" + getPort() + "/upload?password=RAW(p+%w0&r)d)&binary=false";
        // END SNIPPET: e1
    }

    @Test
    public void testRawPassword() throws Exception {
        sendFile(getFtpUrl(), "Hello World", "camel.txt");

        File file = new File(FTP_ROOT_DIR + "/upload/camel.txt");
        assertTrue(file.exists(), "The uploaded file should exists");
        assertEquals("Hello World", IOConverter.toString(file, null));
    }
}
