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
package org.apache.camel.example.netty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;

/**
 * Netty server which returns back an echo of the incoming request.
 */
public final class MyServer {

    private MyServer() {
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.addRouteBuilder(new MyRouteBuilder());
        main.bind("myEncoder", new MyCodecEncoderFactory());
        main.bind("myDecoder", new MyCodecDecoderFactory());
        main.run(args);
    }

    private static class MyRouteBuilder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("netty:tcp://localhost:4444?sync=true&encoders=#myEncoder&decoders=#myDecoder")
                .log("Request:  ${id}:${body}")
                .filter(simple("${body} contains 'beer'"))
                    // use some delay when its beer to make responses interleaved
                    // and make the delay asynchronous
                    .delay(simple("${random(1000,9000)}")).asyncDelayed().end()
                .end()
                .transform(simple("${body}-Echo"))
                .log("Response: ${id}:${body}");
        }
    }
}
