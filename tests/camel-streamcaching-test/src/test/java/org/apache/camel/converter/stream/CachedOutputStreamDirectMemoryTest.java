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
package org.apache.camel.converter.stream;

import java.security.SecureRandom;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

public class CachedOutputStreamDirectMemoryTest extends CamelTestSupport {

    private static final int SPOOL_THRESHOLD = 2 * 1024 * 1024; //Must be greater than -XX:MaxDirectMemorySize in surefire
    private static final long BYTES_TO_WRITE = 2 * SPOOL_THRESHOLD; //Must be greater than SPOOL_THRESHOLD

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setStreamCaching(true);
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().setSpoolThreshold(SPOOL_THRESHOLD); // 24 KB
                from("direct:start").process(new CustomProcessor()).to("mock:result");
            }
        };
    }

    private static class CustomProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            byte[] buffer = new byte[IOHelper.DEFAULT_BUFFER_SIZE];
            SecureRandom random = new SecureRandom();

            CachedOutputStream cos = new CachedOutputStream(exchange);
            long written = 0;
            while (written < BYTES_TO_WRITE) {
                int toWrite = (int) Math.min(IOHelper.DEFAULT_BUFFER_SIZE, BYTES_TO_WRITE - written);
                random.nextBytes(buffer);
                cos.write(buffer, 0, toWrite);
                written += toWrite;
            }
            cos.flush();
            cos.close();

            exchange.getMessage().setBody(cos.getInputStream());
        }
    }

    @Test
    public void oomTest() {
        // This test will trigger an OutOfMemoryError if the stream caching strategy
        // does not handle large streams correctly.
        template.sendBodyAndHeader("direct:start", null, "stream", true);
    }
}
