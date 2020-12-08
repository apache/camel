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
import java.io.FileOutputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIf(value = "org.apache.camel.component.file.remote.services.SftpEmbeddedService#hasRequiredAlgorithms")
public class SftpChangedReadLockTest extends SftpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SftpChangedReadLockTest.class);

    protected String getFtpUrl() {
        return "sftp://localhost:{{ftp.server.port}}/" + service.getFtpRootDir()
               + "/changed?username=admin&password=admin&readLock=changed&readLockCheckInterval=1000&delete=true";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/changed");
        super.setUp();
    }

    @Test
    public void testChangedReadLock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/changed/out/slowfile.dat");

        context.getRouteController().startRoute("foo");

        writeSlowFile();

        assertMockEndpointsSatisfied();

        String content = context.getTypeConverter().convertTo(String.class, new File("target/changed/out/slowfile.dat"));
        String[] lines = content.split(LS);
        assertEquals(20, lines.length, "There should be 20 lines in the file");
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines[i]);
        }
    }

    private void writeSlowFile() throws Exception {
        LOG.debug("Writing slow file...");

        createDirectory(service.getFtpRootDir() + "/changed");
        FileOutputStream fos = new FileOutputStream(service.getFtpRootDir() + "/changed/slowfile.dat", true);
        for (int i = 0; i < 20; i++) {
            fos.write(("Line " + i + LS).getBytes());
            LOG.debug("Writing line " + i);
            Thread.sleep(200);
        }

        fos.flush();
        fos.close();
        LOG.debug("Writing slow file DONE...");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getFtpUrl()).routeId("foo").noAutoStartup().to("file:target/changed/out", "mock:result");
            }
        };
    }

}
