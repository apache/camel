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

import java.io.FileOutputStream;
import java.nio.file.Path;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpChangedReadLockIT extends SftpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SftpChangedReadLockIT.class);

    @TempDir
    Path testDirectory;

    protected String getFtpUrl() {
        return "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}/changed" +
               "?username=admin&password=admin&readLock=changed&readLockCheckInterval=1000&delete=true&knownHostsFile="
               + service.getKnownHostsFile();
    }

    @Test
    public void testChangedReadLock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(testDirectory.resolve("out/slowfile.dat"));

        context.getRouteController().startRoute("foo");

        writeSlowFile();

        MockEndpoint.assertIsSatisfied(context);

        String content = context.getTypeConverter().convertTo(String.class, testDirectory.resolve("out/slowfile.dat").toFile());
        String[] lines = content.split(LS);
        assertEquals(20, lines.length, "There should be 20 lines in the file");
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines[i]);
        }
    }

    private void writeSlowFile() throws Exception {
        LOG.debug("Writing slow file...");

        createDirectory(ftpFile("changed"));
        FileOutputStream fos = new FileOutputStream(ftpFile("changed/slowfile.dat").toFile(), true);
        for (int i = 0; i < 20; i++) {
            fos.write(("Line " + i + LS).getBytes());
            LOG.debug("Writing line {}", i);
            Thread.sleep(200);
        }
        fos.flush();
        fos.close();
        LOG.debug("Writing slow file DONE...");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getFtpUrl()).routeId("foo").noAutoStartup().to(TestSupport.fileUri(testDirectory, "out"), "mock:result");
            }
        };
    }

}
