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
package org.apache.camel.component.smb;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hierynomus.smbj.SmbConfig;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.smb.services.SmbService;
import org.apache.camel.test.infra.smb.services.SmbServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Isolated
class SmbComponentConnectionIT extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(SmbComponentIT.class);

    @RegisterExtension
    public static SmbService service = SmbServiceFactory.createSingletonService();

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    void testSendReceive() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received_send");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("seda:send", "Hello World", Exchange.FILE_NAME, "file_send.doc");

        mock.assertIsSatisfied();
        SmbFile file = mock.getExchanges().get(0).getIn().getBody(SmbFile.class);

        Assertions.assertEquals("Hello World", new String((byte[]) file.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void testDefaultIgnore() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:received_ignore");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("seda:send", "Hello World", Exchange.FILE_NAME, "file_ignore.doc");
        template.sendBodyAndHeaders("seda:send", "Good Bye", Map.of(Exchange.FILE_NAME, "file_ignore.doc",
                SmbConstants.SMB_FILE_EXISTS, GenericFileExist.Ignore.name()));

        mock.assertIsSatisfied();
        SmbFile file = mock.getExchanges().get(0).getIn().getBody(SmbFile.class);
        Assertions.assertEquals("Hello World", new String((byte[]) file.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void testOverride() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:received_override");
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("seda:send", "Hello World22", Exchange.FILE_NAME, "file_override.doc");
        template.sendBodyAndHeaders("seda:send", "Good Bye", Map.of(Exchange.FILE_NAME, "file_override.doc",
                SmbConstants.SMB_FILE_EXISTS, GenericFileExist.Override.name()));

        mock.assertIsSatisfied();
        SmbFile file = mock.getExchanges().get(0).getIn().getBody(SmbFile.class);
        Assertions.assertEquals("Good Bye", new String((byte[]) file.getBody(), StandardCharsets.UTF_8));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                SmbConfig config = SmbConfig.builder()
                        .withTimeout(120, TimeUnit.SECONDS) // Timeout sets Read, Write, and Transact timeouts (default is 60 seconds)
                        .withSoTimeout(180, TimeUnit.SECONDS) // Socket Timeout (default is 0 seconds, blocks forever)
                        .build();
                context.getRegistry().bind("smbConfig", config);

                from("seda:send")
                        .toF("smb:%s/%s?username=%s&password=%s", service.address(), service.shareName(),
                                service.userName(), service.password());

                fromF("smb:%s/%s?username=%s&password=%s&searchPattern=*_override.doc&initialDelay=3000", service.address(),
                        service.shareName(),
                        service.userName(), service.password())
                        .to("mock:received_override");
                fromF("smb:%s/%s?username=%s&password=%s&searchPattern=*_ignore.doc&initialDelay=3000", service.address(),
                        service.shareName(),
                        service.userName(), service.password())
                        .to("mock:received_ignore");
                fromF("smb:%s/%s?username=%s&password=%s&searchPattern=*_send.doc&initialDelay=3000", service.address(),
                        service.shareName(),
                        service.userName(), service.password())
                        .to("mock:received_send");
            }
        };
    }
}
