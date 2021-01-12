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
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for FTP using expression (file language)
 */
public class FtpConsumerMoveExpressionTest extends FtpServerTestSupport {

    @BindToRegistry("myguidgenerator")
    private MyGuidGenerator guid = new MyGuidGenerator();

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/filelanguage?password=admin&delay=5000";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/filelanguage");
    }

    @Test
    public void testMoveUsingExpression() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Reports");

        sendFile(getFtpUrl(), "Reports", "report2.txt");

        assertMockEndpointsSatisfied();

        // give time for consumer to rename file
        Thread.sleep(1000);

        String now = new SimpleDateFormat("yyyyMMdd").format(new Date());
        File file = new File(service.getFtpRootDir() + "/filelanguage/backup/" + now + "/123-report2.bak");
        assertTrue(file.exists(), "File should have been renamed");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl() + "&move=backup/${date:now:yyyyMMdd}/${bean:myguidgenerator}" + "-${file:name.noext}.bak")
                        .to("mock:result");
            }
        };
    }

    public class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }
}
