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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;

/**
 *
 */
public class FtpProducerToDMoveExistingIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}"
               + "/${header.myDir}?password=admin&fileExist=Move&moveExisting=old-${file:onlyname}";
    }

    @Test
    public void testMoveExisting() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        Map<String, Object> headers = new HashMap<>();
        headers.put("myDir", "out");
        headers.put(Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeaders("direct:start", "Hello World", headers);
        template.sendBodyAndHeaders("direct:start", "Bye World", headers);

        MockEndpoint.assertIsSatisfied(context);

        assertFileExists(service.ftpFile("out/old-hello.txt"));
        assertFileExists(service.ftpFile("out/hello.txt"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").toD(getFtpUrl()).to("mock:result");
            }
        };
    }

}
