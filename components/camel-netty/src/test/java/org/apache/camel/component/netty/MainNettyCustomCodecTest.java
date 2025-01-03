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
package org.apache.camel.component.netty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

public class MainNettyCustomCodecTest extends BaseNettyTest {

    // use reaadble bytes
    private byte[] data_eol = new byte[] { 65, 66, 67, 68, 69, 70, 71, 72, 73, 0, 0 };
    private byte[] data = new byte[] { 65, 66, 67, 68, 69, 70, 71, 72, 73 };

    @Test
    public void testMain() throws Exception {
        Main main = new Main();
        main.bind("myCustomDecoder", MyCustomCodec.createMyCustomDecoder());
        main.bind("myCustomDecoder2", MyCustomCodec.createMyCustomDecoder2());
        main.bind("myCustomEncoder", MyCustomCodec.createMyCustomEncoder());
        main.addInitialProperty("camel.component.netty.encoders", "#myCustomEncoder");
        main.addInitialProperty("camel.component.netty.decoders", "#myCustomDecoder,#myCustomDecoder2");
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                String uri = "netty:tcp://localhost:" + getPort() + "?disconnect=true&sync=false&allowDefaultCodec=false";

                from(uri).to("log:input")
                        .process(e -> {
                            byte[] local = e.getMessage().getBody(byte[].class);
                            boolean eq = ObjectHelper.equalByteArray(data, local);
                            if (!eq) {
                                throw new IllegalArgumentException("Data received is not as expected");
                            }
                        });

                from("timer:once?repeatCount=1")
                        .setBody().constant(data_eol) // include null terminator
                        .to(uri);
            }
        });
        main.configure().withDurationMaxMessages(2);
        main.configure().withDurationMaxSeconds(5);
        main.run();

        main.stop();
    }

}
