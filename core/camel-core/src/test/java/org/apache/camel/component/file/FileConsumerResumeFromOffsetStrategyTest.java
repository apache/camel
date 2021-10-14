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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.consumer.FileConsumerResumeStrategy;
import org.apache.camel.component.file.consumer.FileResumeSet;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileConsumerResumeFromOffsetStrategyTest extends ContextTestSupport {

    private static class TestResumeStrategy implements FileConsumerResumeStrategy {
        private static final Logger LOG = LoggerFactory.getLogger(TestResumeStrategy.class);

        @Override
        public long lastOffset(File file) {
            if (!file.getName().startsWith("resume-from-offset")) {
                throw new RuntimeCamelException("Invalid file - resume strategy should not have been called!");
            }

            return 3;
        }

        @Override
        public void resume(FileResumeSet resumeSet) {
            // NO-OP
        }
    }

    @DisplayName("Tests whether we can resume from an offset")
    @Test
    public void testResumeFromOffset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("34567890");

        template.sendBodyAndHeader(fileUri("resumeOff"), "01234567890", Exchange.FILE_NAME, "resume-from-offset.txt");

        // only expect 4 of the 6 sent
        assertMockEndpointsSatisfied();
    }

    @DisplayName("Tests whether we can start from the beginning (i.e.: no resume strategy)")
    @Test
    public void testNoResume() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("01234567890");

        template.sendBodyAndHeader(fileUri("resumeNone"), "01234567890", Exchange.FILE_NAME, "resume-none.txt");

        // only expect 4 of the 6 sent
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                bindToRegistry("testResumeStrategy", new TestResumeStrategy());

                from(fileUri("resumeOff?noop=true&recursive=true&resumeStrategy=#testResumeStrategy"))
                        .convertBodyTo(String.class).to("mock:result");

                from(fileUri("resumeNone?noop=true&recursive=true"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
