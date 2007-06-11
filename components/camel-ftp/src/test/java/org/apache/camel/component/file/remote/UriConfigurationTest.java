/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.remote;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version $Revision: 532790 $
 */
public class UriConfigurationTest extends TestCase {
    protected CamelContext context = new DefaultCamelContext();

    public void testFtpConfigurationAscii() throws Exception {
        Endpoint endpoint = context.getEndpoint("ftp://camel-user@localhost:123/tmp?password=secret");
        assertTrue("Endpoint not an FtpEndpoint: " + endpoint, endpoint instanceof FtpEndpoint);
        FtpEndpoint ftpEndpoint = (FtpEndpoint) endpoint;

        assertEquals("localhost", ftpEndpoint.getConfiguration().getHost());
        assertEquals(123, ftpEndpoint.getConfiguration().getPort());
        assertEquals("camel-user", ftpEndpoint.getConfiguration().getUsername());
        assertEquals("/tmp", ftpEndpoint.getConfiguration().getFile());
        assertEquals(true, ftpEndpoint.getConfiguration().isDirectory());
        assertEquals(false, ftpEndpoint.getConfiguration().isBinary());
    }

    public void testFtpConfigurationBinary() throws Exception {
        Endpoint endpoint = context.getEndpoint("ftp://camel-user@localhost:123/tmp?password=secret&binary=true");
        assertTrue("Endpoint not an FtpEndpoint: " + endpoint, endpoint instanceof FtpEndpoint);
        FtpEndpoint ftpEndpoint = (FtpEndpoint) endpoint;

        assertEquals("localhost", ftpEndpoint.getConfiguration().getHost());
        assertEquals(123, ftpEndpoint.getConfiguration().getPort());
        assertEquals("camel-user", ftpEndpoint.getConfiguration().getUsername());
        assertEquals("/tmp", ftpEndpoint.getConfiguration().getFile());
        assertEquals(true, ftpEndpoint.getConfiguration().isDirectory());
        assertEquals(true, ftpEndpoint.getConfiguration().isBinary());
    }

    public void testFtpConfigurationDefaultPort() throws Exception {
        Endpoint endpoint = context.getEndpoint("ftp://camel-user@localhost/tmp?password=secret");
        assertTrue("Endpoint not an FtpEndpoint: " + endpoint, endpoint instanceof FtpEndpoint);
        FtpEndpoint ftpEndpoint = (FtpEndpoint) endpoint;

        assertEquals("localhost", ftpEndpoint.getConfiguration().getHost());
        assertEquals(21, ftpEndpoint.getConfiguration().getPort());
        assertEquals("camel-user", ftpEndpoint.getConfiguration().getUsername());
        assertEquals("/tmp", ftpEndpoint.getConfiguration().getFile());
        assertEquals(true, ftpEndpoint.getConfiguration().isDirectory());
        assertEquals(false, ftpEndpoint.getConfiguration().isBinary());
    }

    public void testSftpConfigurationDefaultPort() throws Exception {
        Endpoint endpoint = context.getEndpoint("sftp://camel-user@localhost/tmp?password=secret");
        assertTrue("Endpoint not an SftpEndpoint: " + endpoint, endpoint instanceof SftpEndpoint);
        SftpEndpoint sftpEndpoint = (SftpEndpoint) endpoint;

        assertEquals("localhost", sftpEndpoint.getConfiguration().getHost());
        assertEquals(22, sftpEndpoint.getConfiguration().getPort());
        assertEquals("camel-user", sftpEndpoint.getConfiguration().getUsername());
        assertEquals("/tmp", sftpEndpoint.getConfiguration().getFile());
        assertEquals(true, sftpEndpoint.getConfiguration().isDirectory());
        assertEquals(false, sftpEndpoint.getConfiguration().isBinary());
    }

    public void testSftpConfigurationDirectory() throws Exception {
        Endpoint endpoint = context.getEndpoint("sftp://camel-user@localhost:123/tmp?password=secret");
        assertTrue("Endpoint not an SftpEndpoint: " + endpoint, endpoint instanceof SftpEndpoint);
        SftpEndpoint sftpEndpoint = (SftpEndpoint) endpoint;

        assertEquals("localhost", sftpEndpoint.getConfiguration().getHost());
        assertEquals(123, sftpEndpoint.getConfiguration().getPort());
        assertEquals("camel-user", sftpEndpoint.getConfiguration().getUsername());
        assertEquals("/tmp", sftpEndpoint.getConfiguration().getFile());
        assertEquals(true, sftpEndpoint.getConfiguration().isDirectory());
    }

    public void testSftpConfigurationFile() throws Exception {
        Endpoint endpoint = context.getEndpoint("sftp://camel-user@localhost:123/tmp/file?password=secret&directory=false");
        assertTrue("Endpoint not an SftpEndpoint: " + endpoint, endpoint instanceof SftpEndpoint);
        SftpEndpoint sftpEndpoint = (SftpEndpoint) endpoint;

        assertEquals("localhost", sftpEndpoint.getConfiguration().getHost());
        assertEquals(123, sftpEndpoint.getConfiguration().getPort());
        assertEquals("camel-user", sftpEndpoint.getConfiguration().getUsername());
        assertEquals("/tmp/file", sftpEndpoint.getConfiguration().getFile());
        assertEquals(false, sftpEndpoint.getConfiguration().isDirectory());
    }
}
