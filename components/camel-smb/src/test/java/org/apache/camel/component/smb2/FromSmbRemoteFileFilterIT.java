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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FromSmbRemoteFileFilterIT extends SmbServerTestSupport {

    @BindToRegistry("myFilter")
    private final MyFileFilter<Object> filter = new MyFileFilter<>();

    protected String getSmbUrl() {
        return String.format(
                "smb2:%s/%s?username=%s&password=%s&path=/myfilter&searchPattern=*&filter=#myFilter",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    @Test
    public void testSmbFilter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceivedInAnyOrder("Report 1", "Report 2");

        mock.assertIsSatisfied();
    }

    private void prepareSmbServer() {
        // create files on the SMB server
        sendFile(getSmbUrl(), "Hello World", "hello.txt");
        sendFile(getSmbUrl(), "Report 1", "report1.txt");
        sendFile(getSmbUrl(), "Bye World", "bye.txt");
        sendFile(getSmbUrl(), "Report 2", "report2.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbUrl()).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    public static class MyFileFilter<T> implements GenericFileFilter<T> {
        @Override
        public boolean accept(GenericFile<T> file) {
            // we only want report files
            return file.getFileName().startsWith("report");
        }
    }
}
