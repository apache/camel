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
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;


/**
 * @version $Revision$
 */
public class UriConfigurationTest extends TestSupport {
    protected CamelContext context = new DefaultCamelContext();

    public void testFtpConfigurationDefaults() {
        Endpoint endpoint = context.getEndpoint("ftp://hostname");
        assertTrue("Endpoint not an FtpEndpoint: " + endpoint, endpoint instanceof FtpEndpoint);
        FtpEndpoint ftpEndpoint = (FtpEndpoint) endpoint;
        RemoteFileConfiguration config = ftpEndpoint.getConfiguration();
        
        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(21, config.getPort());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertEquals(false, config.isBinary());
        assertEquals(true, config.isDirectory());
    }
    
    public void testSftpConfigurationDefaults() {
        Endpoint endpoint = context.getEndpoint("sftp://hostname");
        assertTrue("Endpoint not an SftpEndpoint: " + endpoint, endpoint instanceof SftpEndpoint);
        SftpEndpoint sftpEndpoint = (SftpEndpoint) endpoint;
        RemoteFileConfiguration config = sftpEndpoint.getConfiguration();
        
        assertEquals("sftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(22, config.getPort());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertEquals(false, config.isBinary());
        assertEquals(true, config.isDirectory());
    }
    
    public void testFtpExplicitConfiguration() {
        Endpoint endpoint = context.getEndpoint("ftp://user@hostname:1021/some/file?password=secret&binary=true&directory=false");
        assertTrue("Endpoint not an FtpEndpoint: " + endpoint, endpoint instanceof FtpEndpoint);
        FtpEndpoint ftpEndpoint = (FtpEndpoint) endpoint;
        RemoteFileConfiguration config = ftpEndpoint.getConfiguration();
        
        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
        assertEquals(false, config.isDirectory());
    }
    
    public void testSftpExplicitConfiguration() {
        Endpoint endpoint = context.getEndpoint("sftp://user@hostname:1021/some/file?password=secret&binary=true&directory=false");
        assertTrue("Endpoint not an SftpEndpoint: " + endpoint, endpoint instanceof SftpEndpoint);
        SftpEndpoint sftpEndpoint = (SftpEndpoint) endpoint;
        RemoteFileConfiguration config = sftpEndpoint.getConfiguration();
        
        assertEquals("sftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
        assertEquals(false, config.isDirectory());
    }
    
    public void testRemoteFileEndpointFiles() {
        assertRemoteFileEndpointFile("ftp://hostname/foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("ftp://hostname/foo/", "foo/");
        assertRemoteFileEndpointFile("ftp://hostname/foo", "foo");
        assertRemoteFileEndpointFile("ftp://hostname/", "");
        assertRemoteFileEndpointFile("ftp://hostname", "");
        assertRemoteFileEndpointFile("ftp://hostname//", "/");
        assertRemoteFileEndpointFile("ftp://hostname//foo/bar", "/foo/bar");
        assertRemoteFileEndpointFile("sftp://user@hostname:123//foo/bar?password=secret", "/foo/bar");
    }
    
    private void assertRemoteFileEndpointFile(String endpointUri, String expectedFile) {
        RemoteFileEndpoint endpoint = resolveMandatoryEndpoint(context, endpointUri, RemoteFileEndpoint.class);
        assertNotNull("Could not find endpoint: " + endpointUri, endpoint);

        String file = endpoint.getConfiguration().getFile();
        assertEquals("For uri: " + endpointUri + " the file is not equal", expectedFile, file);
    }
    
    public void testSftpKnownHostsConfiguration() {
        Endpoint endpoint = context.getEndpoint("sftp://user@hostname:1021/some/file?password=secret&binary=true&directory=false&knownHosts=/home/janstey/.ssh/known_hosts");
        assertTrue("Endpoint not an SftpEndpoint: " + endpoint, endpoint instanceof SftpEndpoint);
        SftpEndpoint sftpEndpoint = (SftpEndpoint) endpoint;
        RemoteFileConfiguration config = sftpEndpoint.getConfiguration();
        
        assertEquals("sftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
        assertEquals(false, config.isDirectory());
        assertEquals("/home/janstey/.ssh/known_hosts", config.getKnownHosts());
    }
}
