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
package org.apache.camel.component.smb2;

import java.io.IOException;
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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmbComponentConnectionIT extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(SmbComponentIT.class);

    @RegisterExtension
    public static SmbService service = SmbServiceFactory.createService();

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void testSmbRead() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(100);

        mock.assertIsSatisfied();
    }

    @Test
    public void testSendReceive() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:received_send");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("seda:send", "Hello World", Exchange.FILE_NAME, "file_send.doc");

        mock.assertIsSatisfied();
        Smb2File file = mock.getExchanges().get(0).getIn().getBody(Smb2File.class);

        Assert.assertEquals("Hello World", new String((byte[]) file.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    public void testDefaultIgnore() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:received_ignore");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("seda:send", "Hello World", Exchange.FILE_NAME, "file_ignore.doc");
        template.sendBodyAndHeaders("seda:send", "Good Bye", Map.of(Exchange.FILE_NAME, "file_ignore.doc",
                Smb2Constants.SMB_FILE_EXISTS, GenericFileExist.Ignore.name()));

        mock.assertIsSatisfied();
        Smb2File file = mock.getExchanges().get(0).getIn().getBody(Smb2File.class);
        Assert.assertEquals("Hello World", new String((byte[]) file.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    public void testOverride() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:received_override");
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("seda:send", "Hello World22", Exchange.FILE_NAME, "file_override.doc");
        template.sendBodyAndHeaders("seda:send", "Good Bye", Map.of(Exchange.FILE_NAME, "file_override.doc",
                Smb2Constants.SMB_FILE_EXISTS, GenericFileExist.Override.name()));

        mock.assertIsSatisfied();
        Smb2File file = mock.getExchanges().get(0).getIn().getBody(Smb2File.class);
        Assert.assertEquals("Good Bye", new String((byte[]) file.getBody(), StandardCharsets.UTF_8));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            private void process(Exchange exchange) throws IOException {
                final Smb2File data = exchange.getMessage().getBody(Smb2File.class);
                final String name = exchange.getMessage().getHeader(Exchange.FILE_NAME, String.class);
                new String((byte[]) data.getBody(), StandardCharsets.UTF_8);
                LOG.debug("Read exchange name {} at {} with contents: {} (bytes {})", name, data.getAbsoluteFilePath(),
                        new String((byte[]) data.getBody(), StandardCharsets.UTF_8), data.getFileLength());
            }

            public void configure() {
                SmbConfig config = SmbConfig.builder()
                        .withTimeout(120, TimeUnit.SECONDS) // Timeout sets Read, Write, and Transact timeouts (default is 60 seconds)
                        .withSoTimeout(180, TimeUnit.SECONDS) // Socket Timeout (default is 0 seconds, blocks forever)
                        .build();
                context.getRegistry().bind("smbConfig", config);

                fromF("smb2:%s/%s?username=%s&password=%s&path=/&smbConfig=#smbConfig", service.address(), service.shareName(),
                        service.userName(), service.password())
                        .to("seda:intermediate");

                from("seda:intermediate?concurrentConsumers=4")
                        .process(this::process)
                        .to("mock:result");

                from("seda:send")
                        .toF("smb2:%s/%s?username=%s&password=%s&path=/", service.address(), service.shareName(),
                                service.userName(), service.password());

                fromF("smb2:%s/%s?username=%s&password=%s&searchPattern=*_override.doc&path=/", service.address(),
                        service.shareName(),
                        service.userName(), service.password())
                        .to("mock:received_override");
                fromF("smb2:%s/%s?username=%s&password=%s&searchPattern=*_ignore.doc&path=/", service.address(),
                        service.shareName(),
                        service.userName(), service.password())
                        .to("mock:received_ignore");
                fromF("smb2:%s/%s?username=%s&password=%s&searchPattern=*_send.doc&path=/", service.address(),
                        service.shareName(),
                        service.userName(), service.password())
                        .to("mock:received_send");
            }
        };
    }
}
