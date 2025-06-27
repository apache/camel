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

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SmbStreamDownloadIT extends SmbServerTestSupport {

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s/uploadstream?username=%s&password=%s&streamDownload=true",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testStreamDownload() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received_send");
        mock.message(0).body().isInstanceOf(SmbFile.class);
        mock.message(0).predicate(e -> {
            Object b = e.getMessage().getBody(SmbFile.class).getBody();
            return b instanceof InputStream;
        });
        mock.expectedMessageCount(1);

        mock.assertIsSatisfied();

        InputStream is = mock.getExchanges().get(0).getIn().getBody(InputStream.class);
        assertNotNull(is);
        String text = IOHelper.loadText(is);
        Assertions.assertEquals("World\n", text);
    }

    private void prepareSmbServer() {
        template.sendBodyAndHeader(getSmbUrl(), "World", Exchange.FILE_NAME, "world.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbUrl()).streamCache("false")
                        .to("mock:received_send");
            }
        };
    }
}
