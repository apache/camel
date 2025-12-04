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

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

public class SmbStreamDownloadFileStreamCachingIT extends SmbServerTestSupport {

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s/uploadstream5?username=%s&password=%s&streamDownload=true&delete=true",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testStreamDownloadToFile() throws Exception {
        FileUtil.removeDir(new File("target/deleteme"));

        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);

        mock.assertIsSatisfied();

        Awaitility.await().untilAsserted(() -> {
            File f = new File("target/deleteme2/world.txt");
            Assertions.assertTrue(f.exists());
        });
    }

    private void prepareSmbServer() {
        template.sendBodyAndHeader(getSmbUrl(), "World", Exchange.FILE_NAME, "world.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbUrl()).streamCache("true").to("mock:input").to("file:target/deleteme2");
            }
        };
    }
}
