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
package org.apache.camel.component.dropbox.integration.producer;

import java.io.IOException;

import com.dropbox.core.DbxException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dropbox.integration.DropboxTestSupport;
import org.apache.camel.component.dropbox.util.DropboxConstants;
import org.apache.camel.component.dropbox.util.DropboxResultHeader;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class DropboxProducerMoveTest extends DropboxTestSupport {

    public static final String COPY_WORKDIR = "/test-workdir";
    public static final String FILE = "file.txt";

    @Before
    public void removeDir() throws DbxException, IOException {
        createDir(COPY_WORKDIR);
        createFile(FILE, "content");
    }

    @Test
    public void testCamelDropbox() throws Exception {
        test("direct:start");
    }

    @Test
    public void testCamelDropboxWithOptionInHeader() throws Exception {
        test("direct:start2");
    }

    private void test(String endpoint) throws InterruptedException {
        template.sendBody(endpoint, null);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(DropboxResultHeader.MOVED_PATH.name(), workdir + "/" + FILE);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to(String.format("dropbox://move?accessToken={{accessToken}}&remotePath=%s&newRemotePath=%s", workdir + "/" + FILE, COPY_WORKDIR + "/" + FILE))
                        .to("mock:result");

                from("direct:start2")
                        .setHeader(DropboxConstants.HEADER_REMOTE_PATH, constant(workdir + "/" + FILE))
                        .setHeader(DropboxConstants.HEADER_NEW_REMOTE_PATH, constant(COPY_WORKDIR + "/" + FILE))
                    .to("dropbox://move?accessToken={{accessToken}}")
                    .to("mock:result");
            }
        };
    }
}
