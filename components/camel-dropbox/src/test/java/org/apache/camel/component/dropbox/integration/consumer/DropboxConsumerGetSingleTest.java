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
package org.apache.camel.component.dropbox.integration.consumer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dropbox.integration.DropboxTestSupport;
import org.apache.camel.component.dropbox.util.DropboxResultHeader;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class DropboxConsumerGetSingleTest extends DropboxTestSupport {

    public static final String FILE_NAME = "myFile.txt";

    @Test
    public void testCamelDropbox() throws Exception {
        final String content = "Hi camels";
        createFile(FILE_NAME, content);

        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(content);
        mock.expectedHeaderReceived(DropboxResultHeader.DOWNLOADED_FILE.name(), String.format("%s/%s", workdir, FILE_NAME));
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(String.format("dropbox://get?accessToken={{accessToken}}&remotePath=%s/%s", workdir, FILE_NAME)).autoStartup(false).id("consumer")
                        .to("mock:result");
            }
        };
    }
}
