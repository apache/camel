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
package org.apache.camel.component.file.remote;

import org.apache.camel.BindToRegistry;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpsEndpointHostnameVerificationTest extends CamelTestSupport {

    @BindToRegistry("sslContextParameters")
    private final SSLContextParameters sslContextParameters = new SSLContextParameters();

    @BindToRegistry("customFtpClient")
    private final FTPSClient customFtpClient = new FTPSClient();

    @Test
    public void createdClientChecksServerHostNameByDefault() throws Exception {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://localhost/dir", FtpsEndpoint.class);

        FTPSClient client = (FTPSClient) endpoint.createFtpClient();

        assertTrue(client.isEndpointCheckingEnabled());
    }

    @Test
    public void createdClientWithSslContextParametersChecksServerHostName() throws Exception {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://localhost/dir?sslContextParameters=#sslContextParameters",
                FtpsEndpoint.class);

        FTPSClient client = (FTPSClient) endpoint.createFtpClient();

        assertTrue(client.isEndpointCheckingEnabled());
    }

    @Test
    public void hostnameVerificationCanBeTurnedOff() throws Exception {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://localhost/dir?hostnameVerification=false", FtpsEndpoint.class);
        FtpsEndpoint endpointWithSslContextParameters = context.getEndpoint(
                "ftps://localhost/other?hostnameVerification=false&sslContextParameters=#sslContextParameters",
                FtpsEndpoint.class);

        assertFalse(((FTPSClient) endpoint.createFtpClient()).isEndpointCheckingEnabled());
        assertFalse(((FTPSClient) endpointWithSslContextParameters.createFtpClient()).isEndpointCheckingEnabled());
    }

    @Test
    public void customFtpClientIsNotChanged() throws Exception {
        FtpsEndpoint endpoint = context.getEndpoint("ftps://localhost/dir?ftpClient=#customFtpClient", FtpsEndpoint.class);

        endpoint.createRemoteFileOperations();

        assertFalse(customFtpClient.isEndpointCheckingEnabled());
    }
}
