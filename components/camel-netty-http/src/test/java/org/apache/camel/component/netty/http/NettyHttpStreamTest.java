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
package org.apache.camel.component.netty.http;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

public class NettyHttpStreamTest extends BaseNettyTest {
    public static final long SIZE =  10 * 256;

    @Test
    public void testUploadStream() {
        //prepare new request
        DefaultExchange request = new DefaultExchange(context);
        request.getIn().setBody("dummy");

        //trigger request
        Exchange response = template.send("direct:upstream-call", request);

        //validate response success
        assertFalse("ups", response.isFailed());

        //validate request stream at server
        MockEndpoint mock = context.getEndpoint("mock:stream-size", MockEndpoint.class);
        Long requestSize = mock.getExchanges().get(0).getIn().getBody(Long.class);
        assertEquals("request size not matching.", SIZE, requestSize.longValue());
    }

    @Test
    public void testDownloadStream() {
        //prepare new request
        DefaultExchange request = new DefaultExchange(context);
        request.getIn().setBody("dummy");

        //trigger request
        Exchange response = template.send("direct:download-call", request);

        //validate response success
        assertFalse("ups", response.isFailed());

        //validate response stream at client
        assertEquals("response size not matching.", SIZE, response.getIn().getBody(Long.class).longValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:upstream-call")
                    .bean(Helper.class, "prepareStream")
                    .to("netty-http:http://localhost:{{port}}/upstream?disableStreamCache=true")
                    .log("get ${body}");

                from("direct:download-call")
                    .to("netty-http:http://localhost:{{port}}/downstream?disableStreamCache=true")
                    .bean(Helper.class, "asyncProcessStream")
                    .log("get ${body}");

                from("netty-http:http://0.0.0.0:{{port}}/upstream?disableStreamCache=true")
                    .bean(Helper.class, "processStream")
                    .to("mock:stream-size");

                from("netty-http:http://0.0.0.0:{{port}}/downstream?disableStreamCache=true")
                    .bean(Helper.class, "prepareStream");
            }
        };
    }
}

final class Helper {
    private Helper() {
    }

    public static void processStream(Exchange exchange) throws Exception {
        InputStream is = exchange.getIn().getBody(InputStream.class);

        byte[] buffer = new byte[1024];
        long read = 0;
        long total = 0;
        while ((read = is.read(buffer, 0, buffer.length)) != -1) {
            total += read;
        }

        exchange.getIn().setBody(new Long(total));
    }

    public static CompletableFuture<Void> asyncProcessStream(Exchange exchange) {
        return CompletableFuture.runAsync(() -> {
            try {
                processStream(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
        });
    }

    public static void prepareStream(Exchange exchange) throws Exception {
        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream(pos);

        exchange.getIn().setBody(pis);

        StreamWriter sw = new StreamWriter(pos, NettyHttpStreamTest.SIZE);
        sw.start();
    }
}

class StreamWriter extends Thread {
    private PipedOutputStream pos;
    private long limit;
    private byte[] content = "hello world stream".getBytes();

    public StreamWriter(PipedOutputStream pos, long limit) {
        this.pos = pos;
        this.limit = limit;
    }

    @Override
    public void run() {
        long count = 0;

        try {
            while (count < limit) {
                long len = content.length < (limit - count) ? content.length : limit - count;
                pos.write(content, 0, (int)len);
                pos.flush();
                count += len;
            }
            pos.close();
        } catch (Exception e) {
        }
    }
}
