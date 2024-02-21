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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 *
 */
public class FromFtpAsyncProcessIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/async/?password=admin&delete=true";
    }

    @Test
    public void testFtpAsyncProcess() throws Exception {
        template.sendBodyAndHeader("file:{{ftp.root.dir}}/async", "Hello World", Exchange.FILE_NAME,
                "hello.txt");
        template.sendBodyAndHeader("file:{{ftp.root.dir}}/async", "Bye World", Exchange.FILE_NAME, "bye.txt");

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", 123);

        // the log file should log that all the ftp client work is done in the
        // same thread (fully synchronous)
        // as the ftp client is not thread safe and must process fully
        // synchronous

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);

        // give time for files to be deleted on ftp server

        File hello = service.ftpFile("async/hello.txt").toFile();
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(hello.exists(), "File should not exist " + hello));

        File bye = service.ftpFile("async/bye.txt").toFile();
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(bye.exists(), "File should not exist " + bye));

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl()).routeId("foo").noAutoStartup().process(new MyAsyncProcessor()).to("mock:result");
            }
        };
    }

    private static class MyAsyncProcessor extends AsyncProcessorSupport {

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        @Override
        public boolean process(final Exchange exchange, final AsyncCallback callback) {
            executor.submit(() -> {

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }

                exchange.getIn().setHeader("foo", 123);
                callback.done(false);
            });

            return false;
        }

    }
}
