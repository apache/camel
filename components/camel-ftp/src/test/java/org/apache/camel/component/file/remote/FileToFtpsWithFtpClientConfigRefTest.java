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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.Test;

/**
 * Test the ftps component over SSL (explicit) and without client authentication
 * 
 * @version 
 */
public class FileToFtpsWithFtpClientConfigRefTest extends FtpsServerExplicitSSLWithoutClientAuthTestSupport {
    
    private String getFtpUrl(boolean in) {
        return "ftps://admin@localhost:" + getPort() 
            + "/tmp2/camel?password=admin&consumer.initialDelay=2000&ftpClient=#ftpsClient"
            + (in ? "In" : "") + "&disableSecureDataChannelDefaults=true&delete=true";
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        jndi.bind("ftpsClient", new FTPSClient("SSL"));
        jndi.bind("ftpsClientIn", new FTPSClient("SSL"));
        return jndi;
    }
    
    @Test
    public void testFromFileToFtp() throws Exception {
        // some platforms cannot test SSL
        if (!canTest) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file:src/main/data?noop=true").log("Putting ${file:name}").to(getFtpUrl(false));

                from(getFtpUrl(true)).to("mock:result");
            }
        };
    }
}