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
package org.apache.camel.component.file.remote.integration;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Test the ftps component over SSL (explicit) and without client authentication
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.FtpsUtil#hasRequiredAlgorithms")
public class FileToFtpsWithFtpClientConfigRefIT extends FtpsServerExplicitSSLWithoutClientAuthTestSupport {

    @BindToRegistry("ftpsClient")
    private final FTPSClient client = new FTPSClient("SSLv3");

    @BindToRegistry("ftpsClientIn")
    private final FTPSClient client1 = new FTPSClient("SSLv3");

    private String getFtpUrl(boolean in) {
        return "ftps://admin@localhost:{{ftp.server.port}}/tmp2/camel?password=admin&initialDelay=2000&ftpClient=#ftpsClient"
               + (in ? "In" : "")
               + "&disableSecureDataChannelDefaults=true&delete=true";
    }

    @Disabled("CAMEL-16784:Disable testFromFileToFtp tests")
    @Test
    public void testFromFileToFtp() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file:src/test/data?noop=true").log("Putting ${file:name}").to(getFtpUrl(false));

                from(getFtpUrl(true)).to("mock:result");
            }
        };
    }
}
