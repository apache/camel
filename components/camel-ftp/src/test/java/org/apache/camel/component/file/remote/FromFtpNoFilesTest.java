/**
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
package org.apache.camel.component.file.remote;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Unit test to verify polling a server with no files to poll.
 */
public class FromFtpNoFilesTest extends FtpServerTestSupport {

    private static final Log LOG = LogFactory.getLog(FromFtpExclusiveReadTest.class);

    private int port = 20020;
    private String ftpUrl = "ftp://admin@localhost:" + port + "/slowfile?password=admin&binary=false&consumer.exclusiveReadLock=true&consumer.delay=2000";

    public int getPort() {
        return port;
    }

    public void testPoolIn3SecondsButNoFiles() throws Exception {
        deleteDirectory("./res/home");
        createDirectory("./res/home/slowfile");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Thread.sleep(3 * 1000L);

        mock.assertIsSatisfied();
    }


    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(ftpUrl).to("mock:result");
            }
        };
    }

}
