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
package org.apache.camel.component.file.remote.sftp;

import java.io.File;

import org.apache.camel.Exchange;
import org.junit.Test;

/**
 * @version 
 */
public class SftpSimpleProduceNotStepwiseTest extends SftpServerTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSftpSimpleProduce() throws Exception {
        if (!canTest()) {
            return;
        }

        template.sendBodyAndHeader("sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "?username=admin&password=admin&stepwise=false", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File(FTP_ROOT_DIR + "/hello.txt");
        assertTrue("File should exist: " + file, file.exists());
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testSftpSimpleSubPathProduce() throws Exception {
        if (!canTest()) {
            return;
        }

        template.sendBodyAndHeader("sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "/mysub?username=admin&password=admin&stepwise=false", "Bye World", Exchange.FILE_NAME, "bye.txt");

        File file = new File(FTP_ROOT_DIR + "/mysub/bye.txt");
        assertTrue("File should exist: " + file, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testSftpSimpleTwoSubPathProduce() throws Exception {
        if (!canTest()) {
            return;
        }

        template.sendBodyAndHeader("sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "/mysub/myother?username=admin&password=admin&stepwise=false", 
            "Farewell World", Exchange.FILE_NAME, "farewell.txt");

        File file = new File(FTP_ROOT_DIR + "/mysub/myother/farewell.txt");
        assertTrue("File should exist: " + file, file.exists());
        assertEquals("Farewell World", context.getTypeConverter().convertTo(String.class, file));
    }
}
