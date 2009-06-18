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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


/**
 * @version $Revision$
 */
public class UriConfigurationTest extends CamelTestSupport {
    protected CamelContext context = new DefaultCamelContext();

    @Test
    public void testFtpConfigurationDefaults() {
        Endpoint endpoint = context.getEndpoint("ftp://hostname");
        assertIsInstanceOf(FtpEndpoint.class, endpoint);
        FtpEndpoint ftpEndpoint = (FtpEndpoint) endpoint;
        RemoteFileConfiguration config = (RemoteFileConfiguration) ftpEndpoint.getConfiguration();

        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(21, config.getPort());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertEquals(false, config.isBinary());
    }

    @Test
    public void testSftpConfigurationDefaults() {
        Endpoint endpoint = context.getEndpoint("sftp://hostname");
        assertIsInstanceOf(SftpEndpoint.class, endpoint);
        SftpEndpoint sftpEndpoint = (SftpEndpoint) endpoint;
        RemoteFileConfiguration config = (RemoteFileConfiguration) sftpEndpoint.getConfiguration();

        assertEquals("sftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(22, config.getPort());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertEquals(false, config.isBinary());
    }

    @Test
    public void testFtpExplicitConfiguration() {
        Endpoint endpoint = context.getEndpoint("ftp://user@hostname:1021/some/file?password=secret&binary=true");
        assertIsInstanceOf(FtpEndpoint.class, endpoint);
        FtpEndpoint ftpEndpoint = (FtpEndpoint) endpoint;
        RemoteFileConfiguration config = (RemoteFileConfiguration) ftpEndpoint.getConfiguration();

        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
    }

    @Test
    public void testSftpExplicitConfiguration() {
        Endpoint endpoint = context.getEndpoint("sftp://user@hostname:1021/some/file?password=secret&binary=true");
        assertIsInstanceOf(SftpEndpoint.class, endpoint);
        SftpEndpoint sftpEndpoint = (SftpEndpoint) endpoint;
        RemoteFileConfiguration config = (RemoteFileConfiguration) sftpEndpoint.getConfiguration();

        assertEquals("sftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
    }
    
    @Test
    public void testRemoteFileEndpointFiles() {
        assertRemoteFileEndpointFile("ftp://hostname/foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("ftp://hostname/foo/bar/", "foo/bar");
        assertRemoteFileEndpointFile("ftp://hostname/foo/", "foo");
        assertRemoteFileEndpointFile("ftp://hostname/foo", "foo");
        assertRemoteFileEndpointFile("ftp://hostname/", "");
        assertRemoteFileEndpointFile("ftp://hostname", "");
        assertRemoteFileEndpointFile("ftp://hostname//", "");
        assertRemoteFileEndpointFile("ftp://hostname//foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("ftp://hostname//foo/bar/", "foo/bar");
        assertRemoteFileEndpointFile("sftp://user@hostname:123//foo/bar?password=secret", "foo/bar");
        assertRemoteFileEndpointFile("sftp://user@hostname:123?password=secret", "");
        assertRemoteFileEndpointFile("sftp://hostname/foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("sftp://hostname/foo/bar/", "foo/bar");
        assertRemoteFileEndpointFile("sftp://hostname/foo/", "foo");
        assertRemoteFileEndpointFile("sftp://hostname/foo", "foo");
        assertRemoteFileEndpointFile("sftp://hostname/", "");
        assertRemoteFileEndpointFile("sftp://hostname", "");
        assertRemoteFileEndpointFile("sftp://hostname//", "");
        assertRemoteFileEndpointFile("sftp://hostname//foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("sftp://hostname//foo/bar/", "foo/bar");
    }

    private void assertRemoteFileEndpointFile(String endpointUri, String expectedFile) {
        RemoteFileEndpoint endpoint = resolveMandatoryEndpoint(context, endpointUri, RemoteFileEndpoint.class);
        assertNotNull("Could not find endpoint: " + endpointUri, endpoint);

        String file = endpoint.getConfiguration().getDirectory();
        assertEquals("For uri: " + endpointUri + " the file is not equal", expectedFile, file);
    }

    @Test
    public void testSftpKnownHostsFileConfiguration() {
        Endpoint endpoint = context.getEndpoint("sftp://user@hostname:1021/some/file?password=secret&binary=true&knownHostsFile=/home/janstey/.ssh/known_hosts");
        assertIsInstanceOf(SftpEndpoint.class, endpoint);
        SftpEndpoint sftpEndpoint = (SftpEndpoint) endpoint;
        SftpConfiguration config = (SftpConfiguration) sftpEndpoint.getConfiguration();

        assertEquals("sftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
        assertEquals("/home/janstey/.ssh/known_hosts", config.getKnownHostsFile());
    }
}
