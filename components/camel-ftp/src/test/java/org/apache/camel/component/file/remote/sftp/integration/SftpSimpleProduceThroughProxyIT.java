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

import com.jcraft.jsch.ProxyHTTP;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpSimpleProduceThroughProxyIT extends SftpServerTestSupport {

    private static HttpProxyServer proxyServer;
    private final int proxyPort = AvailablePortFinder.getNextAvailable();

    @BeforeAll
    public void setupProxy() {
        proxyServer = DefaultHttpProxyServer.bootstrap()
                .withPort(proxyPort)
                .withProxyAuthenticator(new ProxyAuthenticator() {
                    @Override
                    public boolean authenticate(String userName, String password) {
                        return "user".equals(userName) && "password".equals(password);
                    }

                    @Override
                    public String getRealm() {
                        return "myrealm";
                    }
                }).start();
    }

    @AfterAll
    public void cleanup() {
        proxyServer.stop();
    }

    @Test
    public void testSftpSimpleProduceThroughProxy() {
        template.sendBodyAndHeader(
                "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                                   + "?username=admin&password=admin&proxy=#proxy",
                "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        File file = ftpFile("hello.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testSftpSimpleSubPathProduceThroughProxy() {
        template.sendBodyAndHeader(
                "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                                   + "/mysub?username=admin&password=admin&proxy=#proxy&knownHostsFile="
                                   + service.getKnownHostsFile(),
                "Bye World", Exchange.FILE_NAME,
                "bye.txt");

        File file = ftpFile("mysub/bye.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testSftpSimpleTwoSubPathProduceThroughProxy() {
        template.sendBodyAndHeader("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                                   + "/mysub/myother?username=admin&password=admin&proxy=#proxy&knownHostsFile="
                                   + service.getKnownHostsFile(),
                "Farewell World",
                Exchange.FILE_NAME, "farewell.txt");

        File file = ftpFile("mysub/myother/farewell.txt").toFile();
        assertTrue(file.exists(), "File should exist: " + file);
        assertEquals("Farewell World", context.getTypeConverter().convertTo(String.class, file));
    }

    @BindToRegistry("proxy")
    public ProxyHTTP createProxy() {

        final ProxyHTTP proxyHTTP = new ProxyHTTP("localhost", proxyPort);
        proxyHTTP.setUserPasswd("user", "password");
        return proxyHTTP;
    }

}
