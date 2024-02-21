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

import java.io.IOException;
import java.io.InputStream;

import com.hierynomus.smbj.share.File;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.smb.services.SmbService;
import org.apache.camel.test.infra.smb.services.SmbServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmbComponentIT extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(SmbComponentIT.class);

    @RegisterExtension
    public static SmbService service = SmbServiceFactory.createService();

    @Test
    public void testSmbRead() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(100);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            private void process(Exchange exchange) throws IOException {
                final File file = exchange.getMessage().getBody(File.class);
                try (InputStream inputStream = file.getInputStream()) {

                    LOG.debug("Read exchange: {}, with contents: {}", file.getPath(),
                            new String(inputStream.readAllBytes()));
                }
            }

            public void configure() {
                fromF("smb:%s/%s?username=%s&password=%s&path=/", service.address(), service.shareName(),
                        service.userName(), service.password())
                        .process(this::process)
                        .to("mock:result");
            }
        };
    }
}
