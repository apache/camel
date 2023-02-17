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
package org.apache.camel.processor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

/**
 * Tests the processing of a file stream-cache with encryption by the multi-cast processor in the parallel processing
 * mode.
 */
public class MultiCastParallelAndStreamCachingWithEncryptionTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setStreamCaching(true);
                context.getStreamCachingStrategy().setEnabled(true);
                context.getStreamCachingStrategy().setSpoolDirectory(testDirectory().toFile());
                context.getStreamCachingStrategy().setSpoolThreshold(5000L);
                context.getStreamCachingStrategy().setSpoolCipher("AES/CTR/NoPadding");

                from("direct:start").multicast().parallelProcessing().stopOnException().to("direct:a", "direct:b").end()
                        .to("mock:result");

                from("direct:a") //
                        // read stream
                        .process(new SimpleProcessor()).to("mock:resulta");

                from("direct:b") //
                        // read stream concurrently, because of parallel processing
                        .process(new SimpleProcessor()).to("mock:resultb");

            }
        };
    }

    private static class SimpleProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {

            Object body = exchange.getIn().getBody();
            if (body instanceof InputStream) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                IOHelper.copy((InputStream) body, output);
                exchange.getMessage().setBody(output.toByteArray());
            } else {
                throw new RuntimeException("Type " + body.getClass().getName() + " not supported");
            }

        }
    }

    /**
     * Tests the FileInputStreamCache. The sent input stream is transformed to FileInputStreamCache before the
     * multi-cast processor is called.
     *
     * @throws Exception
     */
    @Test
    public void testFileInputStreamCache() throws Exception {

        InputStream resultA = getPayload();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOHelper.copy(resultA, baos);
        IOHelper.close(resultA);
        byte[] resultBytes = baos.toByteArray();

        MockEndpoint mock = getMockEndpoint("mock:resulta");
        mock.expectedBodiesReceived(Collections.singletonList(resultBytes));
        mock = getMockEndpoint("mock:resultb");
        mock.expectedBodiesReceived(Collections.singletonList(resultBytes));

        InputStream in = getPayload();
        try {
            template.sendBody("direct:start", in);
            assertMockEndpointsSatisfied();
        } finally {
            in.close();
        }
    }

    private InputStream getPayload() {
        return MultiCastParallelAndStreamCachingWithEncryptionTest.class.getClassLoader()
                .getResourceAsStream("org/apache/camel/processor/payload10KB.txt");
    }

}
