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

    private static final String COMMAND_VALUE = "commandValue";
    private static final String COMMAND_KEY = "commandKey1";

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
    public void putIfAbsentAlreadyExists() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        
        template.send("direct:putifabsent", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_IF_ABSENT);
            }
        });

        Object value = currentCache().get(KEY_ONE);
        assertThat(value.toString(), is(VALUE_ONE));
        assertEquals(currentCache().size(), 1);
    }
    
    @Test
    public void putIfAbsentNotExists() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        
        template.send("direct:putifabsent", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_IF_ABSENT);
            }
        });

        Object value = currentCache().get(KEY_TWO);
        assertThat(value.toString(), is(VALUE_TWO));
        assertEquals(currentCache().size(), 2);
    }
    
    @Test
    public void notContainsKeyTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        
        Exchange exchange = template.request("direct:containskey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.CONTAINS_KEY);
            }
        });

        assertThat(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), is(false));
    }
    
    @Test
    public void containsKeyTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        
        Exchange exchange = template.request("direct:containskey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.CONTAINS_KEY);
            }
        });

        assertThat(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), is(true));
    }
    
    @Test
    public void notContainsValueTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        
        Exchange exchange = template.request("direct:containsvalue", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.CONTAINS_VALUE);
            }
        });

        assertThat(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), is(false));
    }
    
    @Test
    public void containsValueTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        
        Exchange exchange = template.request("direct:containsvalue", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.CONTAINS_VALUE);
            }
        });

        assertThat(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), is(true));
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
    public void replaceAValueByKey() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replace", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertThat(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), is(VALUE_ONE));
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
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

    @Test
    public void testUriCommandOption() throws Exception {
        template.send("direct:put", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, COMMAND_KEY);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, COMMAND_VALUE);
            }
        });
        String result = (String) currentCache().get(COMMAND_KEY);
        assertEquals(COMMAND_VALUE, result);

        Exchange exchange;
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, COMMAND_KEY);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(COMMAND_VALUE, resultGet);

        exchange = template.send("direct:remove", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, COMMAND_KEY);
            }
        });
        String resultRemove = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(COMMAND_VALUE, resultRemove);
        assertNull(currentCache().get(COMMAND_KEY));
        assertTrue(currentCache().isEmpty());

        currentCache().put(COMMAND_KEY, COMMAND_VALUE);
        currentCache().put("keyTest", "valueTest");

        template.send("direct:clear", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        assertTrue(currentCache().isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer");
                from("direct:put")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=PUT");
                from("direct:putifabsent")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=PUTIFABSENT");
                from("direct:get")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=GET");
                from("direct:remove")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=REMOVE");
                from("direct:clear")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=CLEAR");
                from("direct:replace")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=REPLACE");
                from("direct:containskey")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=CONTAINSKEY");
                from("direct:containsvalue")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=CONTAINSVALUE");
            }
        };
    }
}
