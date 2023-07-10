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

import com.jcraft.jsch.ProxyHTTP;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpSimpleConsumeThroughProxyIT extends SftpServerTestSupport {
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
    public void testSftpSimpleConsumeThroughProxy() throws Exception {
        String expected = "Hello World";

        // create file using regular file
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin&password=admin&delay=10000&disconnect=true&proxy=#proxy&knownHostsFile="
                     + service.getKnownHostsFile()).routeId("foo").noAutoStartup()
                        .to("mock:result");
            }
        };
    }

    @BindToRegistry("proxy")
    public ProxyHTTP createProxy() {

        final ProxyHTTP proxyHTTP = new ProxyHTTP("localhost", proxyPort);
        proxyHTTP.setUserPasswd("user", "password");
        return proxyHTTP;
    }
}
