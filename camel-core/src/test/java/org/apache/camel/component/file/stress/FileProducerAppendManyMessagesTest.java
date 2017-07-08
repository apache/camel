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
package org.apache.camel.component.file.stress;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;

/**
 *
 */
@Ignore("Manual test")
public class FileProducerAppendManyMessagesTest extends ContextTestSupport {

    private boolean enabled;

    @Override
    protected void setUp() throws Exception {
        if (!enabled) {
            return;
        }

        deleteDirectory("target/big");
        createDirectory("target/big");

        // create a big file
        File file = new File("target/big/data.txt");
        FileOutputStream fos = new FileOutputStream(file);
        for (int i = 0; i < 100000; i++) {
            String s = "Hello World this is a long line with number " + i + LS;
            fos.write(s.getBytes());
        }
        fos.close();

        super.setUp();
    }

    public void testBigFile() throws Exception {
        if (!enabled) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:done");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(2 * 60000);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/big")
                    .split(body().tokenize(LS)).streaming()
                        .to("log:processing?groupSize=1000")
                        .to("file:target/out/also-big.txt?fileExist=Append")
                    .end()
                    .to("mock:done");
            }
        };
    }
}
