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
package org.apache.camel.component.file.remote.integration;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpSimpleConsumeStreamingPartialReadIT extends FtpServerTestSupport {

    @Test
    public void testFtpSimpleConsumeAbsolute() throws Exception {
        String expected = "Hello World";

        // create file using regular file

        // FTP Server does not support absolute path, so lets simulate it
        String path = service.ftpFile("tmp/mytemp").toString();
        template.sendBodyAndHeader("file:" + path, expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);

        InputStream is = mock.getExchanges().get(0).getIn().getBody(InputStream.class);
        assertNotNull(is);

        // Wait a little bit for the move to finish.
        File resultFile = new File(path + File.separator + "failed", "hello.txt");
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(resultFile.exists()));
        assertFalse(resultFile.isDirectory());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ftp://localhost:{{ftp.server.port}}"
                     + "/tmp/mytemp?username=admin&password=admin&delay=10000&disconnect=true&streamDownload=true"
                     + "&move=done&moveFailed=failed&stepwise=false")
                        .routeId("foo").noAutoStartup().process(new Processor() {

                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getIn().getBody(InputStream.class).read();
                            }
                        }).to("mock:result").process(new Processor() {

                            @Override
                            public void process(Exchange exchange) throws Exception {
                                throw new Exception("INTENTIONAL ERROR");
                            }
                        });
            }
        };
    }
}
