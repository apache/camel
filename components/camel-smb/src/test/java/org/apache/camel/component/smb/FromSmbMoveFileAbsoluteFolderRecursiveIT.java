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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FromSmbMoveFileAbsoluteFolderRecursiveIT extends SmbServerTestSupport {

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=/moverecurse&recursive=true" +
                             "&move=/.done/${file:name}.old&initialDelay=2500&delay=5000",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    @Test
    public void testPollFileAndShouldBeMoved() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello", "Bye", "Goodday");
        mock.assertIsSatisfied();

        // verify files reside in the SMB dir within the docker container
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello",
                        new String(copyFileContentFromContainer("/data/rw/.done/hello.txt.old"))));

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Bye",
                        new String(copyFileContentFromContainer("/data/rw/.done/bye/bye.txt.old"))));

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Goodday",
                        new String(copyFileContentFromContainer("/data/rw/.done/goodday/goodday.txt.old"))));
    }

    private void prepareSmbServer() {
        template.sendBodyAndHeader(getSmbUrl(), "Hello", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getSmbUrl(), "Bye", Exchange.FILE_NAME, "bye/bye.txt");
        template.sendBodyAndHeader(getSmbUrl(), "Goodday", Exchange.FILE_NAME, "goodday/goodday.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbUrl()).to("mock:result");
            }
        };
    }
}
