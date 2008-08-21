/**
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

import org.apache.camel.component.file.FileComponent;
import org.apache.camel.converter.IOConverter;

/**
 * Unit test to verify that Camel can build remote directory on FTP server if missing (full or part of).
 */
public class FtpProducerBuildDirectoryTest extends FtpServerTestSupport {

    private int port = 20089;
    private String ftpUrl = "ftp://admin@localhost:" + port + "/upload/user/claus?binary=false&password=admin";

    public int getPort() {
        return port;
    }

    public void testProduceAndBuildFullRemotFolderTest() throws Exception {
        deleteDirectory("./res/home/");

        template.sendBodyAndHeader(ftpUrl, "Hello World", FileComponent.HEADER_FILE_NAME, "claus.txt");

        File file = new File("./res/home/upload/user/claus/claus.txt");
        file = file.getAbsoluteFile();
        assertTrue("The uploaded file should exists", file.exists());
        assertEquals("Hello World", IOConverter.toString(file));
    }

}
