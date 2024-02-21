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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.consumer.DirectoryEntriesResumeAdapter;
import org.apache.camel.component.file.consumer.FileOffsetResumeAdapter;
import org.apache.camel.component.file.consumer.FileResumeAdapter;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.resume.TransientResumeStrategy;
import org.apache.camel.support.resume.Resumables;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileConsumerResumeFromOffsetStrategyTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(FileConsumerResumeFromOffsetStrategyTest.class);

    private static class TestFileResumeAdapter implements FileResumeAdapter, FileOffsetResumeAdapter {
        private GenericFile<File> resumable;

        @Override
        public void setResumePayload(GenericFile<File> resumable) {
            if (!resumable.getFile().getName().startsWith("resume-from-offset")) {
                throw new RuntimeCamelException("Invalid file - resume strategy should not have been called!");
            }

            this.resumable = resumable;
        }

        @Override
        public void resume() {
            if (resumable != null) {
                resumable.updateLastOffsetValue(3L);
                resumable = null;
            }
        }
    }

    private static class FailResumeAdapter
            implements FileResumeAdapter, DirectoryEntriesResumeAdapter {

        @Override
        public void resume() {

        }

        @Override
        public boolean resume(File file) {
            return false;
        }

    }

    private static final TransientResumeStrategy FAIL_RESUME_STRATEGY = new TransientResumeStrategy(new FailResumeAdapter());

    @DisplayName("Tests whether it can resume from an offset")
    @Test
    public void testResumeFromOffset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("34567890");

        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.FILE_NAME, "resume-from-offset.txt");
        headers.put(Exchange.OFFSET, Resumables.of("resume-from-offset.txt", 3L));

        template.sendBodyAndHeaders(fileUri("resumeOff"), "01234567890", headers);

        // only expect 4 of the 6 sent
        assertMockEndpointsSatisfied();
    }

    @DisplayName("Tests whether it a missing offset causes a failure")
    @Test
    public void testMissingOffset() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("34567890");
        mock.expectedMessageCount(2);

        template.sendBodyAndHeader(fileUri("resumeMissingOffset"), "01234567890", Exchange.FILE_NAME, "resume-from-offset.txt");

        MockEndpoint.assertWait(2, TimeUnit.SECONDS, mock);

        List<Exchange> exchangeList = mock.getExchanges();
        Assertions.assertFalse(exchangeList.isEmpty(), "It should have received a few messages");
    }

    @DisplayName("Tests whether it a missing offset does not cause a failure when using intermittent mode")
    @Test
    public void testMissingOffsetWithIntermittentMode() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("01234567890");

        template.sendBodyAndHeader(fileUri("resumeMissingOffsetIntermittent"), "01234567890", Exchange.FILE_NAME,
                "resume-from-offset.txt");

        assertMockEndpointsSatisfied();

        List<Exchange> exchangeList = mock.getExchanges();
        Assertions.assertFalse(exchangeList.isEmpty(), "It should have received a few messages");
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
            public void configure() {

                bindToRegistry("myResumeStrategy", new TransientResumeStrategy(new TestFileResumeAdapter()));
                bindToRegistry("resumeNotToBeCalledStrategy", FAIL_RESUME_STRATEGY);

                from(fileUri("resumeOff?noop=true&recursive=true"))
                        .resumable("myResumeStrategy")
                        .setHeader(Exchange.OFFSET,
                                constant(Resumables.of("resume-none.txt", 3)))
                        .log("${body}")
                        .convertBodyTo(String.class).to("mock:result");

                from(fileUri("resumeMissingOffset?noop=true&recursive=true"))
                        .resumable().resumeStrategy("resumeNotToBeCalledStrategy")
                        .log("${body}")
                        .convertBodyTo(String.class).to("mock:result");

                from(fileUri("resumeMissingOffsetIntermittent?noop=true&recursive=true"))
                        .resumable().resumeStrategy("resumeNotToBeCalledStrategy").intermittent(true)
                        .log("${body}")
                        .convertBodyTo(String.class).to("mock:result");

                from(fileUri("resumeNone?noop=true&recursive=true"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
