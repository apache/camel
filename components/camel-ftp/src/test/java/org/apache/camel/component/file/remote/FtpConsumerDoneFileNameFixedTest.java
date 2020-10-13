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
package org.apache.camel.component.file.remote;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class FtpConsumerDoneFileNameFixedTest extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/done?password=admin&initialDelay=0&delay=100&stepwise=false";
    }

    @Test
    public void testDoneFileName() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        // wait a bit and it should not pickup the written file as there are no
        // done file
        Thread.sleep(1000);

        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        // write the done file
        template.sendBodyAndHeader(getFtpUrl(), "", Exchange.FILE_NAME, "fin.dat");

        assertMockEndpointsSatisfied();

        // give time for done file to be deleted
        Thread.sleep(1000);

        // done file should be deleted now
        File file = new File(FTP_ROOT_DIR + "done/fin.dat");
        assertFalse(file.exists(), "Done file should be deleted: " + file);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getFtpUrl() + "&doneFileName=fin.dat").convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
