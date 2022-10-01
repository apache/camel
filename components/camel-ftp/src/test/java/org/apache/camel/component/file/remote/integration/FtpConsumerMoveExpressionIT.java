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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.awaitility.Awaitility.await;

/**
 * Unit test for FTP using expression (file language)
 */
public class FtpConsumerMoveExpressionIT extends FtpServerTestSupport {

    @BindToRegistry("myguidgenerator")
    private final MyGuidGenerator guid = new MyGuidGenerator();

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/filelanguage?password=admin&delay=5000";
    }

    @Test
    public void testMoveUsingExpression() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Reports");

        sendFile(getFtpUrl(), "Reports", "report2.txt");

        MockEndpoint.assertIsSatisfied(context);

        // give time for consumer to rename file
        String now = new SimpleDateFormat("yyyyMMdd").format(new Date());
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFileExists(service.ftpFile("filelanguage/backup/" + now + "/123-report2.bak")));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl() + "&move=backup/${date:now:yyyyMMdd}/${bean:myguidgenerator}" + "-${file:name.noext}.bak")
                        .to("mock:result");
            }
        };
    }

    public static class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }
}
