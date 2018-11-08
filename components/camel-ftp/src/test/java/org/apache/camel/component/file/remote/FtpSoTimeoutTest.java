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

import java.net.ServerSocket;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class used to demonstrate the problematic disconnect sequence of the {@link FtpOperations}.
 * <p>
 * Setting the logging level of {@code org.apache.camel.file.remote} to {@code TRACE} will provide useful information
 * 
 * @author l.chiarello
 *
 */
public class FtpSoTimeoutTest extends CamelTestSupport {

    private ServerSocket serverSocket;

    // --- Set up
    
    @Override
    @Before
    public void setUp() throws Exception {
        // the created server socket makes it possible for the FTP client to establish the socket connection.
        // However, no message will ever be sent back, thus a read timeout should occur within FTPClient#__getReply()
        serverSocket = new ServerSocket(0);
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (serverSocket != null) {
            serverSocket.close();
        }
    }
    
    @Override
    protected int getShutdownTimeout() {
        return 5; // speedup graceful shutdown
    }
    
    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:with")
                    .to("ftp://localhost:" + serverSocket.getLocalPort()
                        + "?ftpClient=#myftpclient&connectTimeout=300&soTimeout=300&reconnectDelay=100");
                
                from("direct:without")
                    .to("ftp://localhost:" + serverSocket.getLocalPort()
                        + "?connectTimeout=300&soTimeout=300&reconnectDelay=100");
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        FTPClient ftpClient = new FTPClient();
        ftpClient.setDefaultTimeout(300);
        JndiRegistry registry = super.createRegistry();
        registry.bind("myftpclient", ftpClient);
        return registry;
    }
    
    // --- Tests
    
    @Test(timeout = 10000, expected = CamelExecutionException.class)
    public void testWithDefaultTimeout() throws Exception {
        // send exchange to the route using the custom FTPClient (with a default timeout)
        // the soTimeout triggers in time and test is successful
        template.sendBody("direct:with", "");
    }
    
    @Test(timeout = 10000, expected = CamelExecutionException.class)
    public void testWithoutDefaultTimeout() throws Exception {
        // send exchange to the route using the default FTPClient (without a default timeout)
        // the soTimeout never triggers and test fails after its own timeout
        template.sendBody("direct:without", "");
    }
}
