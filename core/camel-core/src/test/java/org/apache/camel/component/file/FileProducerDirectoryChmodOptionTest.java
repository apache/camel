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
package org.apache.camel.component.file;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Date;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(OS.WINDOWS)
public class FileProducerDirectoryChmodOptionTest extends ContextTestSupport {

    private static final String SUBDIR_NAME = "testdir";

    @Test
    public void testWriteValidNoDir() throws Exception {
        runChmodCheck("NoDir", null, "rwxr-xr-x");
    }

    @Test
    public void testWriteValidChmod0755() throws Exception {
        runChmodCheck("0755", "rwxrwxrwx", "rwxr-xr-x");
    }

    @Test
    public void testWriteValidChmod666() throws Exception {
        runChmodCheck("666", "rwxrwxrwx", "rw-rw-rw-");
    }

    private void runChmodCheck(String routeSuffix, String expectedDirectoryPermissions, String expectedPermissions)
            throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:chmod" + routeSuffix);
        mock.expectedMessageCount(1);
        String testFileName = "chmod" + routeSuffix + ".txt";
        String testFileContent = "Writing file with chmod " + routeSuffix + " option at " + new Date();
        String testFilePath = Path.of(SUBDIR_NAME, testFileName).toString();
        mock.expectedFileExists(testFile(testFilePath), testFileContent);

        template.sendBodyAndHeader("direct:write" + routeSuffix, testFileContent, Exchange.FILE_NAME, testFilePath);

        if (expectedDirectoryPermissions != null) {
            Set<PosixFilePermission> permissions
                    = Files.getPosixFilePermissions(testDirectory(SUBDIR_NAME), LinkOption.NOFOLLOW_LINKS);
            assertEquals(expectedDirectoryPermissions, PosixFilePermissions.toString(permissions));
            assertEquals(expectedDirectoryPermissions.replace("-", "").length(), permissions.size());
        }

        if (expectedPermissions != null) {
            Set<PosixFilePermission> permissions
                    = Files.getPosixFilePermissions(testFile(testFilePath), LinkOption.NOFOLLOW_LINKS);
            assertEquals(expectedPermissions, PosixFilePermissions.toString(permissions));
            assertEquals(expectedPermissions.replace("-", "").length(), permissions.size());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Valid chmod values
                from("direct:write666").to(fileUri("?chmodDirectory=777&chmod=666")).to("mock:chmod666");

                from("direct:write0755").to(fileUri("?chmodDirectory=777&chmod=0755")).to("mock:chmod0755");

                from("direct:writeNoDir").to(fileUri("?chmod=0755")).to("mock:chmodNoDir");

            }
        };
    }
}
