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
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SftpSetOperationsTest extends SftpServerTestSupport {

    @Test
    public void testSftpSetOperations() throws Exception {
        if (!canTest()) {
            return;
        }

        String preferredAuthentications = "password,publickey";
        String uri = "sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "?username=admin&password=admin&ciphers=blowfish-cbc" + "&preferredAuthentications=password,publickey";
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

        // test setting the cipher doesn't interfere with message payload
        File file = new File(FTP_ROOT_DIR + "/hello.txt");
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));

        // did we actually set the preferedAuthentifications
        SftpEndpoint endpoint = context.getEndpoint(uri, SftpEndpoint.class);
        assertEquals(preferredAuthentications, endpoint.getConfiguration().getPreferredAuthentications());
    }
}
