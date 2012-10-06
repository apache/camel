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

import org.apache.camel.Exchange;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test to verify shutdown.
 */
public class FtpShutdownCompleteCurrentTaskOnlyTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/pending?password=admin&initialDelay=5000";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating files on the server that we want to unit
        String ftpUrl = "ftp://admin@localhost:" + getPort() + "/pending/?password=admin";
        template.sendBodyAndHeader(ftpUrl, "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(ftpUrl, "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(ftpUrl, "C", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader(ftpUrl, "D", Exchange.FILE_NAME, "d.txt");
        template.sendBodyAndHeader(ftpUrl, "E", Exchange.FILE_NAME, "e.txt");
    }

    @Test
    public void testShutdownCompleteCurrentTaskOnly() throws Exception {
        // give it 20 seconds to shutdown
        context.getShutdownStrategy().setTimeout(20);

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMinimumMessageCount(1);
        Thread.sleep(50);

        assertMockEndpointsSatisfied();

        // shutdown during processing
        context.stop();

        // should NOT route all 5
        assertTrue("Should NOT complete all messages, was: " + bar.getReceivedCounter(), bar.getReceivedCounter() < 5);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getFtpUrl()).routeId("route1")
                    // let it complete only current task so we shutdown faster
                    .shutdownRunningTask(ShutdownRunningTask.CompleteCurrentTaskOnly)
                    .delay(1000).to("seda:foo");

                from("seda:foo").routeId("route2").to("mock:bar");
            }
        };
    }
}