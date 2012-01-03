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
package org.apache.camel.component.mina2;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.mina.core.filterchain.IoFilterChain.Entry;
import org.junit.Test;

/**
 * For unit testing the <tt>noDefaultCodec</tt> option.
 */
public class Mina2NoDefaultCodecTest extends BaseMina2Test {

    int port1;
    int port2;

    @Test
    public void testFilter() throws Exception {
        port1 = getPort();
        port2 = getNextPort();

        final String uri1 = "mina2:tcp://localhost:" + port1 + "?allowDefaultCodec=false";
        final String uri2 = "mina2:tcp://localhost:" + port2;

        context.addRoutes(new RouteBuilder() {

            public void configure() throws Exception {
                from(uri1).to("mock:result");
                from(uri2).to("mock:result");
            }
        });

        Mina2Producer producer1 = (Mina2Producer) context.getEndpoint(uri1).createProducer();
        Mina2Producer producer2 = (Mina2Producer) context.getEndpoint(uri2).createProducer();
        List<Entry> filters1 = producer1.getFilterChain().getAll();
        List<Entry> filters2 = producer2.getFilterChain().getAll();
        assertTrue(filters1.size() < filters2.size());
    }
}
