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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpChmodDirectoryIT extends SftpServerTestSupport {

    @Test
    public void testSftpChmodDirectoryWriteable() {
        template.sendBodyAndHeader(
                "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}/folder" +
                                   "?username=admin&password=admin&chmod=777&chmodDirectory=770&knownHostsFile="
                                   + service.getKnownHostsFile(),
                "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        File path = ftpFile("folder/hello.txt").getParent().toFile();
        assertTrue(path.canRead(), "Path should have permission readable: " + path);
        assertTrue(path.canWrite(), "Path should have permission writeable: " + path);
    }

}
