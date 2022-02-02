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
package org.apache.camel.component.file;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.consumer.FileResumeSet;
import org.apache.camel.component.file.consumer.FileSetResumeStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FileConsumerResumeStrategyTest extends ContextTestSupport {

    private static class TestResumeStrategy implements FileSetResumeStrategy {
        private List<String> processedFiles = Arrays.asList("0.txt", "1.txt", "2.txt");
        private FileResumeSet resumeSet;

        @Override
        public void resume(FileResumeSet resumeSet) {
            this.resumeSet = Objects.requireNonNull(resumeSet);

            resume();
        }

        @Override
        public void resume() {
            if (resumeSet != null) {
                resumeSet.resumeEach(f -> !processedFiles.contains(f.getName()));
            }
        }

        @Override
        public void start() throws Exception {

        }
    }

    @Test
    public void testResume() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("3", "4", "5", "6");

        template.sendBodyAndHeader(fileUri("resume"), "0", Exchange.FILE_NAME, "0.txt");
        template.sendBodyAndHeader(fileUri("resume"), "1", Exchange.FILE_NAME, "1.txt");
        template.sendBodyAndHeader(fileUri("resume"), "2", Exchange.FILE_NAME, "2.txt");
        template.sendBodyAndHeader(fileUri("resume"), "3", Exchange.FILE_NAME, "3.txt");
        template.sendBodyAndHeader(fileUri("resume"), "4", Exchange.FILE_NAME, "4.txt");
        template.sendBodyAndHeader(fileUri("resume"), "5", Exchange.FILE_NAME, "5.txt");
        template.sendBodyAndHeader(fileUri("resume"), "6", Exchange.FILE_NAME, "6.txt");

        // only expect 4 of the 6 sent
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                bindToRegistry("testResumeStrategy", new TestResumeStrategy());

                from(fileUri("resume?noop=true&recursive=true&resumeStrategy=#testResumeStrategy"))
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

}
