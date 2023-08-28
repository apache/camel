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
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Date;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.WINDOWS)
public class FileProducerChmodOptionTest extends ContextTestSupport {

    @Test
    public void testWriteValidChmod0755() throws Exception {
        runChmodCheck("0755", "rwxr-xr-x");
    }

    @Test
    public void testWriteValidChmod666() throws Exception {
        runChmodCheck("666", "rw-rw-rw-");
    }

    private void runChmodCheck(String routeSuffix, String expectedPermissions) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:chmod" + routeSuffix);
        mock.expectedMessageCount(1);
        String testFileName = "chmod" + routeSuffix + ".txt";
        String testFileContent = "Writing file with chmod " + routeSuffix + " option at " + new Date();
        mock.expectedFileExists(testFile(testFileName), testFileContent);

        template.sendBodyAndHeader("direct:write" + routeSuffix, testFileContent, Exchange.FILE_NAME, testFileName);

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(testFile(testFileName), LinkOption.NOFOLLOW_LINKS);
        assertEquals(expectedPermissions, PosixFilePermissions.toString(permissions));
        assertEquals(expectedPermissions.replace("-", "").length(), permissions.size());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInvalidChmod() {
        FailedToCreateRouteException e = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {

                @Override
                public void configure() {
                    from("direct:writeBadChmod1").to(fileUri("?chmod=abc")).to("mock:badChmod1");
                }
            });
        }, "Expected FailedToCreateRouteException");

        assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
        PropertyBindingException pbe = assertIsInstanceOf(PropertyBindingException.class, e.getCause().getCause());
        assertEquals("chmod", pbe.getPropertyName());
        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, pbe.getCause());
        assertTrue(iae.getMessage().contains("chmod option [abc] is not valid"));
    }

    /**
     * Write a file without chmod set, should work normally and not throw an exception for invalid chmod value
     *
     * @throws Exception
     */
    @Test
    public void testWriteNoChmod() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:noChmod");
        mock.expectedMessageCount(1);
        String testFileName = "noChmod.txt";
        String testFileContent = "Writing file with no chmod option at " + new Date();
        mock.expectedFileExists(testFile(testFileName), testFileContent);
        template.sendBodyAndHeader("direct:writeNoChmod", testFileContent, Exchange.FILE_NAME, testFileName);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Valid chmod values
                from("direct:write666").to(fileUri("?chmod=666")).to("mock:chmod666");

                from("direct:write0755").to(fileUri("?chmod=0755")).to("mock:chmod0755");

                // No chmod
                from("direct:writeNoChmod").to(fileUri()).to("mock:noChmod");
            }
        };
    }
}
