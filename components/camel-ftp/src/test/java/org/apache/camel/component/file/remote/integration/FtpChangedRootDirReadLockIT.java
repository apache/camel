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
package org.apache.camel.component.file.remote.integration;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class FtpChangedRootDirReadLockIT extends FtpServerTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(FtpChangedRootDirReadLockIT.class);

    @TempDir
    Path testDirectory;

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}"
               + "/?password=admin&readLock=changed&readLockCheckInterval=1000&delete=true";
    }

    @Test
    public void testChangedReadLock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(testDirectory.resolve("slowfile.dat"));

        writeSlowFile();

        MockEndpoint.assertIsSatisfied(context);

        List<String> lines = Files.readAllLines(testDirectory.resolve("slowfile.dat"));
        assertEquals(20, lines.size(), "There should be 20 lines in the file");
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines.get(i));
        }
    }

    private void writeSlowFile() throws Exception {
        LOG.debug("Writing slow file...");

        createDirectory(service.getFtpRootDir() + "/");
        FileOutputStream fos = new FileOutputStream(service.ftpFile("slowfile.dat").toFile(), true);
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
                from(getFtpUrl()).to(TestSupport.fileUri(testDirectory), "mock:result");
            }
        };
    }

}
