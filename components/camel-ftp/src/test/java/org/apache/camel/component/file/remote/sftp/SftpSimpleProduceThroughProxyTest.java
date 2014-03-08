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
package org.apache.camel.component.file.remote.sftp;

import java.io.File;

import com.jcraft.jsch.ProxyHTTP;
import org.apache.camel.Exchange;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.Test;
import org.littleshoot.proxy.DefaultHttpProxyServer;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthorizationHandler;

public class SftpSimpleProduceThroughProxyTest extends SftpServerTestSupport {

    private final int proxyPort = AvailablePortFinder.getNextAvailable(25000);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSftpSimpleProduceThroughProxy() throws Exception {
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

        template.sendBodyAndHeader("sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "?username=admin&password=admin&proxy=#proxy", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File(FTP_ROOT_DIR + "/hello.txt");
        assertTrue("File should exist: " + file, file.exists());
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
        
        proxyServer.stop();
    }

    @Test
    public void testSftpSimpleSubPathProduceThroughProxy() throws Exception {
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

        template.sendBodyAndHeader("sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "/mysub?username=admin&password=admin&proxy=#proxy", "Bye World", Exchange.FILE_NAME, "bye.txt");

        File file = new File(FTP_ROOT_DIR + "/mysub/bye.txt");
        assertTrue("File should exist: " + file, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));

        proxyServer.stop();
    }

    @Test
    public void testSftpSimpleTwoSubPathProduceThroughProxy() throws Exception {
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

        template.sendBodyAndHeader("sftp://localhost:" + getPort() + "/" + FTP_ROOT_DIR + "/mysub/myother?username=admin&password=admin&proxy=#proxy", "Farewell World", Exchange.FILE_NAME,
            "farewell.txt");

        File file = new File(FTP_ROOT_DIR + "/mysub/myother/farewell.txt");
        assertTrue("File should exist: " + file, file.exists());
        assertEquals("Farewell World", context.getTypeConverter().convertTo(String.class, file));

        proxyServer.stop();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        final ProxyHTTP proxyHTTP = new ProxyHTTP("localhost", proxyPort);
        proxyHTTP.setUserPasswd("user", "password");
        jndi.bind("proxy", proxyHTTP);
        return jndi;
    }

}
