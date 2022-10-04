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

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FromFileToFtpSplitParallelIT extends FtpServerTestSupport {

    private static final int SIZE = 5000;

    @TempDir
    Path testDirectory;

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/tmp2/big?password=admin";
    }

    @Test
    public void testSplit() throws Exception {
        // create big file
        FileOutputStream fos = new FileOutputStream(testDirectory.toString() + "/bigdata.txt");
        for (int i = 0; i < SIZE; i++) {
            String line = "ABCDEFGHIJKLMNOPQRSTUVWXYZ-" + i + "\n";
            fos.write(line.getBytes(StandardCharsets.UTF_8));
        }
        IOHelper.close(fos);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        context.getRouteController().startAllRoutes();

        MockEndpoint.assertIsSatisfied(context, 1, TimeUnit.MINUTES);

        // use memory profiler
        // Thread.sleep(99999999);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                ThreadPoolProfile tpp
                        = new ThreadPoolProfileBuilder("ftp-pool").poolSize(5).maxPoolSize(10).maxQueueSize(1000).build();
                context.getExecutorServiceManager().registerThreadPoolProfile(tpp);

                onException().maximumRedeliveries(5).redeliveryDelay(1000);

                from(fileUri(testDirectory)).noAutoStartup().routeId("foo")
                    .split(body().tokenize("\n")).executorService("ftp-pool")
                        .to(getFtpUrl())
                        .to("log:line?groupSize=100")
                    .end()
                    .log("End of splitting")
                    .to("mock:result");
            }
        };
    }
}
