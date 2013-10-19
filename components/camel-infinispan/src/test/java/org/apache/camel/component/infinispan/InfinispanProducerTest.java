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
package org.apache.camel.component.infinispan;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;

public class InfinispanProducerTest extends InfinispanTestSupport {

    @Test
    public void keyAndValueArePublishedWithDefaultOperation() throws Exception {
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
            }
        });

        Object value = currentCache().get(KEY_ONE);
        assertThat(value.toString(), is(VALUE_ONE));
    }

    @Test
    public void publishKeyAndValueByExplicitlySpecifyingTheOperation() throws Exception {
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT);
            }
        });

        Object value = currentCache().get(KEY_ONE);
        assertThat(value.toString(), is(VALUE_ONE));
    }

    @Test
    public void putOperationReturnsThePreviousValue() throws Exception {
        currentCache().put(KEY_ONE, "existing value");

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT);
            }
        });

        assertThat(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), is("existing value"));
    }

    @Test
    public void retrievesAValueByKey() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.GET);
            }
        });

        assertThat(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), is(VALUE_ONE));
    }

    @Test
    public void deletesExistingValueByKey() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REMOVE);
            }
        });

        assertThat(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), is(VALUE_ONE));

        Object value = currentCache().get(KEY_ONE);
        assertThat(value, is(nullValue()));
    }

    @Test
    public void clearsAllValues() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        assertThat(currentCache().isEmpty(), is(false));

        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.CLEAR);
            }
        });

        assertThat(currentCache().isEmpty(), is(true));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer");
            }
        };
    }
}
