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

import java.util.Comparator;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FromSmbRemoteFileSorterIT extends SmbServerTestSupport {

    @BindToRegistry("mySorter")
    private final MyRemoteFileSorter sorter = new MyRemoteFileSorter();

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=/sorter&sorter=#mySorter&initialDelay=3000",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    @Test
    public void testSmbSorter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.expectedBodiesReceived("Hello Copenhagen", "Hello London", "Hello Paris");
        mock.assertIsSatisfied();
    }

    private void prepareSmbServer() {
        sendFile(getSmbUrl(), "Hello Paris", "paris.txt");
        sendFile(getSmbUrl(), "Hello London", "london.txt");
        sendFile(getSmbUrl(), "Hello Copenhagen", "copenhagen.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbUrl()).to("mock:result");
            }
        };
    }

    public static class MyRemoteFileSorter implements Comparator<GenericFile<?>> {
        @Override
        public int compare(GenericFile<?> o1, GenericFile<?> o2) {
            return o1.getFileNameOnly().compareToIgnoreCase(o2.getFileNameOnly());
        }
    }
}
