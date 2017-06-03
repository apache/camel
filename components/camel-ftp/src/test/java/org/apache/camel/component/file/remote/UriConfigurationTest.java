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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class UriConfigurationTest extends CamelTestSupport {

    @Test
    public void testFtpConfigurationDefaults() {
        FtpEndpoint<?> endpoint = context.getEndpoint("ftp://hostname", FtpEndpoint.class);
        RemoteFileConfiguration config = endpoint.getConfiguration();

        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(21, config.getPort());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertEquals(false, config.isBinary());
        assertEquals(RemoteFileConfiguration.PathSeparator.UNIX, config.getSeparator());
    }

    @Test
    public void testSftpConfigurationDefaults() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname", SftpEndpoint.class);
        RemoteFileConfiguration config = endpoint.getConfiguration();

        assertEquals("sftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(22, config.getPort());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertEquals(false, config.isBinary());
        assertEquals(RemoteFileConfiguration.PathSeparator.UNIX, config.getSeparator());
    }
    
    @Test
    public void testFtpsConfigurationDefaults() {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://hostname", FtpsEndpoint.class);
        FtpsConfiguration config = endpoint.getFtpsConfiguration();

        assertEquals("ftps", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(21, config.getPort());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertEquals(false, config.isBinary());
        assertEquals(false, config.isImplicit());
        assertEquals("TLS", config.getSecurityProtocol());
        assertEquals(RemoteFileConfiguration.PathSeparator.UNIX, config.getSeparator());
    }

    @Test
    public void testFtpsExplicitConfigurationDefaults() {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://hostname:990?isImplicit=true", FtpsEndpoint.class);
        FtpsConfiguration config = endpoint.getFtpsConfiguration();

        assertEquals("ftps", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(990, config.getPort());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertEquals(false, config.isBinary());
        assertEquals(true, config.isImplicit());
        assertEquals("TLS", config.getSecurityProtocol());
    }

    @Test
    public void testFtpExplicitConfiguration() {
        FtpEndpoint<?> endpoint = context.getEndpoint("ftp://user@hostname:1021/some/file?password=secret&binary=true", FtpEndpoint.class);
        RemoteFileConfiguration config = endpoint.getConfiguration();

        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
    }

    @Test
    public void testSftpExplicitConfiguration() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://user@hostname:1021/some/file?password=secret&binary=true", SftpEndpoint.class);
        RemoteFileConfiguration config = endpoint.getConfiguration();

        assertEquals("sftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
    }
    
    @Test
    public void testFtpsExplicitConfiguration() {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://user@hostname:1021/some/file?password=secret&binary=true&securityProtocol=SSL&isImplicit=true", FtpsEndpoint.class);
        FtpsConfiguration config = endpoint.getFtpsConfiguration();

        assertEquals("ftps", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
        assertEquals(true, config.isImplicit());
        assertEquals("SSL", config.getSecurityProtocol());
    }
    
    @Test
    public void testRemoteFileEndpointFiles() {
        assertRemoteFileEndpointFile("ftp://hostname/foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("ftp://hostname/foo/bar/", "foo/bar/");
        assertRemoteFileEndpointFile("ftp://hostname/foo/", "foo/");
        assertRemoteFileEndpointFile("ftp://hostname/foo", "foo");
        assertRemoteFileEndpointFile("ftp://hostname/", "");
        assertRemoteFileEndpointFile("ftp://hostname", "");
        assertRemoteFileEndpointFile("ftp://hostname//", "");
        assertRemoteFileEndpointFile("ftp://hostname//foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("ftp://hostname//foo/bar/", "foo/bar/");
        assertRemoteFileEndpointFile("sftp://user@hostname:123//foo/bar?password=secret", "foo/bar");
        assertRemoteFileEndpointFile("sftp://user@hostname:123?password=secret", "");
        assertRemoteFileEndpointFile("sftp://hostname/foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("sftp://hostname/foo/bar/", "foo/bar/");
        assertRemoteFileEndpointFile("sftp://hostname/foo/", "foo/");
        assertRemoteFileEndpointFile("sftp://hostname/foo", "foo");
        assertRemoteFileEndpointFile("sftp://hostname/", "");
        assertRemoteFileEndpointFile("sftp://hostname", "");
        assertRemoteFileEndpointFile("sftp://hostname//", "");
        assertRemoteFileEndpointFile("sftp://hostname//foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("sftp://hostname//foo/bar/", "foo/bar/");
        assertRemoteFileEndpointFile("ftps://user@hostname:123//foo/bar?password=secret", "foo/bar");
        assertRemoteFileEndpointFile("ftps://user@hostname:123?password=secret", "");
        assertRemoteFileEndpointFile("ftps://hostname/foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("ftps://hostname/foo/bar/", "foo/bar/");
        assertRemoteFileEndpointFile("ftps://hostname/foo/", "foo/");
        assertRemoteFileEndpointFile("ftps://hostname/foo", "foo");
        assertRemoteFileEndpointFile("ftps://hostname/", "");
        assertRemoteFileEndpointFile("ftps://hostname", "");
        assertRemoteFileEndpointFile("ftps://hostname//", "");
        assertRemoteFileEndpointFile("ftps://hostname//foo/bar", "foo/bar");
        assertRemoteFileEndpointFile("ftps://hostname//foo/bar/", "foo/bar/");
        assertRemoteFileEndpointFile("ftps://hostname//////foo/bar/", "foo/bar/");
    }

    private void assertRemoteFileEndpointFile(String endpointUri, String expectedFile) {
        RemoteFileEndpoint<?> endpoint = resolveMandatoryEndpoint(context, endpointUri, RemoteFileEndpoint.class);
        assertNotNull("Could not find endpoint: " + endpointUri, endpoint);

        String file = endpoint.getConfiguration().getDirectory();
        assertEquals("For uri: " + endpointUri + " the file is not equal", expectedFile, file);
    }

    @Test
    public void testSftpKnownHostsFileConfiguration() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://user@hostname:1021/some/file?password=secret&binary=true&knownHostsFile=/home/janstey/.ssh/known_hosts", SftpEndpoint.class);
        SftpConfiguration config = endpoint.getConfiguration();

        assertEquals("sftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals(true, config.isBinary());
        assertEquals("/home/janstey/.ssh/known_hosts", config.getKnownHostsFile());
    }

    @Test
    public void testPasswordInContextPathConfiguration() {
        FtpEndpoint<?> endpoint = context.getEndpoint("ftp://user:secret@hostname:1021/some/file", FtpEndpoint.class);
        RemoteFileConfiguration config = endpoint.getConfiguration();
        
        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals(1021, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("secret", config.getPassword());
    }
    
    @Test
    public void testStartingDirectoryWithDot() throws Exception {
        FtpEndpoint<?> endpoint = context.getEndpoint("ftp://user@hostname?password=secret", FtpEndpoint.class);
        FtpConfiguration config = endpoint.getConfiguration();
        config.setHost("somewhere");
        config.setDirectory("temp.dir");
        endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // do nothing
            }
        });
    }

    @Test
    public void testPathSeparatorAuto() {
        FtpEndpoint<?> endpoint = context.getEndpoint("ftp://hostname/foo/bar?separator=Auto", FtpEndpoint.class);
        RemoteFileConfiguration config = endpoint.getConfiguration();

        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals("foo/bar", config.getDirectory());
        assertEquals(RemoteFileConfiguration.PathSeparator.Auto, config.getSeparator());

        assertEquals("foo/bar/hello.txt", config.normalizePath("foo/bar/hello.txt"));
        assertEquals("foo\\bar\\hello.txt", config.normalizePath("foo\\bar\\hello.txt"));
    }

    @Test
    public void testPathSeparatorUnix() {
        FtpEndpoint<?> endpoint = context.getEndpoint("ftp://hostname/foo/bar?separator=UNIX", FtpEndpoint.class);
        RemoteFileConfiguration config = endpoint.getConfiguration();

        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals("foo/bar", config.getDirectory());
        assertEquals(RemoteFileConfiguration.PathSeparator.UNIX, config.getSeparator());

        assertEquals("foo/bar/hello.txt", config.normalizePath("foo/bar/hello.txt"));
        assertEquals("foo/bar/hello.txt", config.normalizePath("foo\\bar\\hello.txt"));
    }

    @Test
    public void testPathSeparatorWindows() {
        FtpEndpoint<?> endpoint = context.getEndpoint("ftp://hostname/foo/bar?separator=Windows", FtpEndpoint.class);
        RemoteFileConfiguration config = endpoint.getConfiguration();

        assertEquals("ftp", config.getProtocol());
        assertEquals("hostname", config.getHost());
        assertEquals("foo/bar", config.getDirectory());
        assertEquals(RemoteFileConfiguration.PathSeparator.Windows, config.getSeparator());

        assertEquals("foo\\bar\\hello.txt", config.normalizePath("foo/bar/hello.txt"));
        assertEquals("foo\\bar\\hello.txt", config.normalizePath("foo\\bar\\hello.txt"));
    }

}