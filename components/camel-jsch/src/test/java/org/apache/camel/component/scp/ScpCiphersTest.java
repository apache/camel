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
package org.apache.camel.component.scp;

import com.jcraft.jsch.JSch;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The ciphers option applies to the session of the endpoint, and leaves the static configuration of JSch, which applies
 * to every JSch session in the JVM (such as the sftp sessions of camel-ftp), as it is.
 */
public class ScpCiphersTest extends ScpServerTestSupport {

    public ScpCiphersTest() {
        super(false);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + getScpPath() + "?recursive=true&delete=true")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testCiphersAreSetOnTheSessionOnly() throws Exception {
        assumeTrue(this.isSetupComplete());

        String s2c = JSch.getConfig("cipher.s2c");
        String c2s = JSch.getConfig("cipher.c2s");
        try {
            getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

            String uri = getScpUri() + "?username=admin&password=admin&useUserKnownHostsFile=false&ciphers=aes128-ctr";
            template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

            MockEndpoint.assertIsSatisfied(context);
            // was set to aes128-ctr for every JSch session in the JVM
            assertEquals(s2c, JSch.getConfig("cipher.s2c"));
            assertEquals(c2s, JSch.getConfig("cipher.c2s"));
        } finally {
            JSch.setConfig("cipher.s2c", s2c);
            JSch.setConfig("cipher.c2s", c2s);
        }
    }
}
