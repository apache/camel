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

import com.jcraft.jsch.ProxyHTTP;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.littleshoot.proxy.DefaultHttpProxyServer;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthorizationHandler;

public class SftpSimpleConsumeThroughProxyTest extends SftpServerTestSupport {

    private final int proxyPort = AvailablePortFinder.getNextAvailable();

    @Test
    public void testSftpSimpleConsumeThroughProxy() throws Exception {
        if (!canTest()) {
            return;
        }

        // start http proxy
        HttpProxyServer proxyServer = new DefaultHttpProxyServer(proxyPort);
        proxyServer.addProxyAuthenticationHandler(new ProxyAuthorizationHandler() {
            @Override
            public boolean authenticate(String userName, String password) {
                return "user".equals(userName) && "password".equals(password);
            }
        });
        proxyServer.start();

        String expected = "Hello World";

        // create file using regular file
        template.sendBodyAndHeader("file://" + FTP_ROOT_DIR, expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();

        proxyServer.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "?username=admin&password=admin&delay=10000&disconnect=true&proxy=#proxy").routeId("foo").noAutoStartup()
                    .to("mock:result");
            }
        };
    }

    @BindToRegistry("proxy")
    public ProxyHTTP createProxy() throws Exception {

        final ProxyHTTP proxyHTTP = new ProxyHTTP("localhost", proxyPort);
        proxyHTTP.setUserPasswd("user", "password");
        return proxyHTTP;
    }
}
