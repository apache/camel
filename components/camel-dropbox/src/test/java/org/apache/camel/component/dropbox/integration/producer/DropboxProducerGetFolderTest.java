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
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dropbox.integration.DropboxTestSupport;
import org.apache.camel.component.dropbox.util.DropboxConstants;
import org.apache.camel.component.dropbox.util.DropboxResultHeader;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class DropboxProducerGetFolderTest extends DropboxTestSupport {

    public static final String FILE_NAME1 = "myFile.txt";
    public static final String FILE_NAME2 = "myFile2.txt";
    private static final String CONTENT1 = "content1";
    private static final String CONTENT2 = "content2";

    @Before
    public void createFile() throws IOException {
        createFile(FILE_NAME1, CONTENT1);
        createFile(FILE_NAME2, CONTENT2);
    }

    @Test
    public void testCamelDropbox() throws Exception {
        test("direct:start");
    }

    @Test
    public void testCamelDropboxWithOptionInHeader() throws Exception {
        test("direct:start2");
    }


    @Test
    public void testCamelDropboxHeaderHasPriorityOnParameter() throws Exception {
        test("direct:start3");
    }

    private void test(String endpoint) throws InterruptedException {
        template.sendBody(endpoint, null);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.message(0).header(DropboxResultHeader.DOWNLOADED_FILES.name()).contains(String.format("%s/%s", workdir, FILE_NAME1));
        mock.message(0).header(DropboxResultHeader.DOWNLOADED_FILES.name()).contains(String.format("%s/%s", workdir, FILE_NAME2));
        mock.assertIsSatisfied();

        final Map<String, byte[]> items = mock.getExchanges().get(0).getIn().getBody(Map.class);
        assertEquals(CONTENT1, new String(items.get(String.format("%s/%s", workdir, FILE_NAME1))));
        assertEquals(CONTENT2, new String(items.get(String.format("%s/%s", workdir, FILE_NAME2))));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("dropbox://get?accessToken={{accessToken}}&remotePath=" + workdir)
                        .to("mock:result");

                from("direct:start2")
                        .setHeader(DropboxConstants.HEADER_REMOTE_PATH, constant(workdir))
                    .to("dropbox://get?accessToken={{accessToken}}")
                    .to("mock:result");


                from("direct:start3")
                        .setHeader(DropboxConstants.HEADER_REMOTE_PATH, constant(workdir))
                    .to("dropbox://get?accessToken={{accessToken}}&remotePath=/aWrongPath")
                    .to("mock:result");

            }
        };
    }
}
