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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpProducerRecipientListIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/list?password=admin";
    }

    @Test
    public void testProducerRecipientList() {
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", getFtpUrl() + "&fileName=hello.txt");
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", getFtpUrl() + "&fileName=bye.txt");
        template.sendBodyAndHeader("direct:start", "Hi World", "foo", getFtpUrl() + "&fileName=hi.txt");

        File file1 = service.ftpFile("list/hello.txt").toFile();
        assertTrue(file1.exists(), "File should exists " + file1);

        File file2 = service.ftpFile("list/bye.txt").toFile();
        assertTrue(file1.exists(), "File should exists " + file2);

        File file3 = service.ftpFile("list/hi.txt").toFile();
        assertTrue(file1.exists(), "File should exists " + file3);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").recipientList(header("foo"));
            }
        };
    }
}
