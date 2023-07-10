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
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpConsumerAutoCreateIT extends SftpServerTestSupport {
    protected String getFtpUrl() {
        return "sftp://admin@localhost:{{ftp.server.port}}/{{ftp.root.dir}}/foo/bar/baz/xxx?password=admin&knownHostsFile="
               + service.getKnownHostsFile();
    }

    @AfterEach
    public void cleanupDir() {
        FileUtil.removeDir(new File(service.getFtpRootDir().toFile(), "/foo/bar/baz/xxx"));
    }

    @Test
    public void testAutoCreate() {
        SftpEndpoint endpoint = (SftpEndpoint) this.getMandatoryEndpoint(getFtpUrl() + "&autoCreate=true");
        endpoint.start();
        endpoint.getExchanges();
        assertTrue(ftpFile("foo/bar/baz/xxx").toFile().exists());
        // producer should create necessary subdirs
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "sub1/sub2/hello.txt");
        assertTrue(ftpFile("foo/bar/baz/xxx/sub1/sub2").toFile().exists());

        // to see if another connect causes problems with autoCreate=true
        endpoint.stop();
        endpoint.start();
        endpoint.getExchanges();
    }

    @Test
    public void testNoAutoCreate() {
        SftpEndpoint endpoint = (SftpEndpoint) this.getMandatoryEndpoint(getFtpUrl() + "&autoCreate=false");
        endpoint.start();

        assertThrows(GenericFileOperationFailedException.class, () -> endpoint.getExchanges(),
                "Should fail with 550 No such directory.");
    }

}
