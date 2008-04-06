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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class FromFtpToMockTest extends FtpServerTestSupport {
    protected MockEndpoint resultEndpoint;
    protected String expectedBody = "Hello there!";
    protected String port = "20010";
    protected String ftpUrl = "ftp://admin@localhost:" + port + "/tmp/camel?password=admin";

    public void testFtpRoute() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived(expectedBody);

        // TODO when we support multiple marshallers for messages
        // we can support passing headers over files using serialized/XML files
        //resultEndpoint.message(0).header("cheese").isEqualTo(123);

        template.sendBodyAndHeader(ftpUrl, expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();

        // let some time pass to let the consumer etc. properly do its business before closing
        Thread.sleep(1000);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(ftpUrl).to("mock:result");
            }
        };
    }

    public String getPort() {
        return port;
    }

}
