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
package org.apache.camel.component.redis;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.RedisTemplate;

@RunWith(MockitoJUnitRunner.class)
public class RedisTestSupport extends CamelTestSupport {

    @Mock
    protected RedisTemplate<String, String> redisTemplate;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("spring-redis://localhost:6379?redisTemplate=#redisTemplate");
            }
        };
    }

    protected Object sendHeaders(final Object... headers) {
        Exchange exchange = template.send(new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                for (int i = 0; i < headers.length; i = i + 2) {
                    in.setHeader(headers[i].toString(), headers[i + 1]);
                }
            }
        });

        return exchange.getIn().getBody();
    }
}
