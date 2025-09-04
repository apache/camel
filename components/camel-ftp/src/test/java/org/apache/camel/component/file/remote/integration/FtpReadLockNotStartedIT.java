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

import java.nio.file.Path;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.remote.FtpConsumer;
import org.apache.camel.component.file.strategy.GenericFileNoOpProcessStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.shaded.org.awaitility.Awaitility;

public class FtpReadLockNotStartedIT extends FtpServerTestSupport {

    @TempDir
    Path testDirectory;

    @BindToRegistry("myLock")
    private final GenericFileProcessStrategy lock = new MyReadLock();

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}"
               + "/notstarted?password=admin&processStrategy=#myLock&idempotent=true";
    }

    @Test
    public void testIdempotentEager() throws Exception {
        FtpConsumer consumer = (FtpConsumer) context.getRoute("myRoute").getConsumer();
        Assertions.assertTrue(consumer.getEndpoint().isIdempotent());
        Assertions.assertTrue(consumer.getEndpoint().getIdempotentEager());
        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) consumer.getEndpoint().getIdempotentRepository();
        Assertions.assertEquals(0, repo.getCacheSize());

        context.getRouteController().startAllRoutes();

        // this file is not okay and is not started
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        template.sendBodyAndHeader("file:{{ftp.root.dir}}/notstarted", "Bye World", Exchange.FILE_NAME,
                "bye.txt");
        mock.assertIsSatisfied(2000);
        Assertions.assertEquals(0, repo.getCacheSize());

        // this file is okay
        mock.reset();
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("file:{{ftp.root.dir}}/notstarted", "Hello World", Exchange.FILE_NAME,
                "hello.txt");
        mock.assertIsSatisfied();

        Awaitility.await().untilAsserted(() -> {
            Assertions.assertEquals(1, repo.getCacheSize());
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getFtpUrl()).routeId("myRoute").autoStartup(false)
                        .to(TestSupport.fileUri(testDirectory, "out"), "mock:result");
            }
        };
    }

    private static class MyReadLock extends GenericFileNoOpProcessStrategy {

        @Override
        public boolean begin(
                GenericFileOperations operations, GenericFileEndpoint endpoint, Exchange exchange, GenericFile file)
                throws Exception {
            String name = FileUtil.stripPath(file.getFileName());
            return "hello.txt".equals(name);
        }
    }

}
