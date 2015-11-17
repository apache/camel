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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.infinispan.commons.util.concurrent.NotifyingFuture;
import org.junit.Test;

public class InfinispanProducerTest extends InfinispanTestSupport {

    private static final String COMMAND_VALUE = "commandValue";
    private static final String COMMAND_KEY = "commandKey1";
    private static final long LIFESPAN_TIME = 5;
    private static final long LIFESPAN_FOR_MAX_IDLE = -1;
    private static final long MAX_IDLE_TIME = 3;

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
        assertEquals(VALUE_ONE, value.toString());
    }
    
    @Test
    public void cacheSizeTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        currentCache().put(KEY_TWO, VALUE_TWO);

        Exchange exchange = template.request("direct:size", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.SIZE);
            }
        });

        Integer cacheSize = exchange.getIn().getHeader(InfinispanConstants.RESULT, Integer.class);
        assertEquals(cacheSize, new Integer(2));
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
        assertEquals(VALUE_ONE, value.toString());
    }
    
    @Test
    public void publishKeyAndValueAsync() throws Exception {
        Exchange exchange = template.send("direct:putasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
            }
        });

        Thread.sleep(10000);
        NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
        assertEquals(Boolean.TRUE, resultPutAsync.isDone());
        
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
    }

    @Test
    public void publishKeyAndValueAsyncWithLifespan() throws Exception {
        Exchange exchange = template.send("direct:putasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        Thread.sleep(1000);
        NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
        assertEquals(Boolean.TRUE, resultPutAsync.isDone());
        
        Object value = currentCache().get(KEY_ONE);
        assertEquals(value.toString(), VALUE_ONE);  
        
        Thread.sleep(6000);
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }
    
    @Test
    public void publishKeyAndValueAsyncWithLifespanAndMaxIdle() throws Exception {
        Exchange exchange = template.send("direct:putasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        Thread.sleep(1000);
        NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
        assertEquals(Boolean.TRUE, resultPutAsync.isDone());
        
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        
        Thread.sleep(10000);
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }
    
    @Test
    public void publishMapNormal() throws Exception {
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> map = new HashMap<String, String>();
                map.put(KEY_ONE, VALUE_ONE);
                map.put(KEY_TWO, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.MAP, map);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_ALL);
            }
        });

        assertEquals(2, currentCache().size());
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        value = currentCache().get(KEY_TWO);
        assertEquals(VALUE_TWO, value.toString());
    }
    
    @Test
    public void publishMapWithLifespan() throws Exception {
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> map = new HashMap<String, String>();
                map.put(KEY_ONE, VALUE_ONE);
                map.put(KEY_TWO, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.MAP, map);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_ALL);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        assertEquals(2, currentCache().size());
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        value = currentCache().get(KEY_TWO);
        assertEquals(VALUE_TWO, value.toString());
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        Exchange exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
            }
        });
        resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }
    
    @Test
    public void publishMapWithLifespanAndMaxIdleTime() throws Exception {
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> map = new HashMap<String, String>();
                map.put(KEY_ONE, VALUE_ONE);
                map.put(KEY_TWO, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.MAP, map);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_ALL);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        assertEquals(2, currentCache().size());
        
        Thread.sleep(10000);
        
        Exchange exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
            }
        });
        resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(null);
    }
    
    @Test
    public void publishMapNormalAsync() throws Exception {
        template.send("direct:putallasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> map = new HashMap<String, String>();
                map.put(KEY_ONE, VALUE_ONE);
                map.put(KEY_TWO, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.MAP, map);
            }
        });
        
        Thread.sleep(100);
        assertEquals(2, currentCache().size());
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        value = currentCache().get(KEY_TWO);
        assertEquals(VALUE_TWO, value.toString());
    }
    
    @Test
    public void publishMapWithLifespanAsync() throws Exception {
        template.send("direct:putallasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> map = new HashMap<String, String>();
                map.put(KEY_ONE, VALUE_ONE);
                map.put(KEY_TWO, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.MAP, map);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_ALL);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        Thread.sleep(100);
        assertEquals(2, currentCache().size());
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        value = currentCache().get(KEY_TWO);
        assertEquals(VALUE_TWO, value.toString());
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        Exchange exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
            }
        });
        resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }
    
    @Test
    public void publishMapWithLifespanAndMaxIdleTimeAsync() throws Exception {
        template.send("direct:putallasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> map = new HashMap<String, String>();
                map.put(KEY_ONE, VALUE_ONE);
                map.put(KEY_TWO, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.MAP, map);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_ALL);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });
        
        Thread.sleep(100);
        assertEquals(2, currentCache().size());
        
        Thread.sleep(10000);
        
        Exchange exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
            }
        });
        resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
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
        assertEquals(VALUE_ONE, value.toString());
        assertEquals(1, currentCache().size());
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
        assertEquals(VALUE_TWO, value.toString());
        assertEquals(2, currentCache().size());
    }
    
    @Test
    public void putIfAbsentKeyAndValueAsync() throws Exception {
        Exchange exchange = template.send("direct:putifabsentasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
            }
        });

        Thread.sleep(10000);
        NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
        assertEquals(Boolean.TRUE, resultPutAsync.isDone());
        
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
    }

    @Test
    public void putIfAbsentKeyAndValueAsyncWithLifespan() throws Exception {
        Exchange exchange = template.send("direct:putifabsentasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        Thread.sleep(1000);
        NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
        assertEquals(Boolean.TRUE, resultPutAsync.isDone());
        
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        
        Thread.sleep(6000);
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }
    
    @Test
    public void putIfAbsentKeyAndValueAsyncWithLifespanAndMaxIdle() throws Exception {
        Exchange exchange = template.send("direct:putifabsentasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        Thread.sleep(1000);
        NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
        assertEquals(Boolean.TRUE, resultPutAsync.isDone());
        
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        
        Thread.sleep(10000);
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
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

        Boolean cacheContainsKey = exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class);
        assertFalse(cacheContainsKey);
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

        Boolean cacheContainsKey = exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class);
        assertTrue(cacheContainsKey);
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

        Boolean cacheContainsValue = exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class);
        assertFalse(cacheContainsValue);
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

        Boolean cacheContainsValue = exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class);
        assertTrue(cacheContainsValue);
    }
    
    @Test
    public void publishKeyAndValueWithLifespan() throws Exception {
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT);
            }
        });

        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        
        Exchange exchange;
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(VALUE_ONE, resultGet);
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
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

        String result = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals("existing value", result);
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

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
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

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
    }
    
    @Test
    public void replaceAValueByKeyWithLifespan() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replace", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }
    
    @Test
    public void replaceAValueByKeyWithLifespanAndMaxIdleTime() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replace", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
        
        Thread.sleep(10000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }

    @Test
    public void replaceAValueByKeyWithOldValue() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replace", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
    }
    
    @Test
    public void replaceAValueByKeyWithLifespanWithOldValue() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replace", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
        
        Thread.sleep(LIFESPAN_TIME * 1100);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }
    
    @Test
    public void replaceAValueByKeyWithLifespanAndMaxIdleTimeWithOldValue() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replace", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
        
        Thread.sleep(10000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }    
    
    
    @Test
    public void replaceAValueByKeyAsync() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replaceasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
    }
    
    @Test
    public void replaceAValueByKeyWithLifespanAsync() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replaceasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(null, resultGet);
    }
    
    @Test
    public void replaceAValueByKeyWithLifespanAndMaxIdleTimeAsync() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replaceasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
        
        Thread.sleep(10000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }
    
    @Test
    public void replaceAValueByKeyAsyncWithOldValue() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replaceasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
    }
    
    @Test
    public void replaceAValueByKeyWithLifespanAsyncWithOldValue() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replaceasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
        
        Thread.sleep(LIFESPAN_TIME * 1100);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
    }
    
    @Test
    public void replaceAValueByKeyWithLifespanAndMaxIdleTimeAsyncWithOldValue() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replaceasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OLD_VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.SECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.SECONDS.toString());
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));
        
        Thread.sleep(10000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertNull(resultGet);
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

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));

        Object value = currentCache().get(KEY_ONE);
        assertNull(value);
    }
    
    @Test
    public void deletesExistingValueByKeyAsync() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:removeasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REMOVE_ASYNC);
            }
        });

        Thread.sleep(100);
        NotifyingFuture fut = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
        assertTrue(fut.isDone());

        Object value = currentCache().get(KEY_ONE);
        assertNull(value);
    }
    
    @Test
    public void deletesExistingValueByKeyWithValue() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REMOVE);
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));

        Object value = currentCache().get(KEY_ONE);
        assertNull(value);
    }
    
    @Test
    public void deletesExistingValueByKeyAsyncWithValue() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:removeasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REMOVE_ASYNC);
            }
        });

        Thread.sleep(100);
        NotifyingFuture fut = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
        assertTrue(fut.isDone());

        Object value = currentCache().get(KEY_ONE);
        assertNull(value);
    }

    @Test
    public void clearsAllValues() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        assertFalse(currentCache().isEmpty());

        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.CLEAR);
            }
        });

        assertTrue(currentCache().isEmpty());
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
    
    @Test
    public void clearAsyncTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        currentCache().put(KEY_TWO, VALUE_TWO);

        Exchange exchange = template.request("direct:clearasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.CLEAR_ASYNC);
            }
        });

        Thread.sleep(100);
        NotifyingFuture fut = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
        assertTrue(fut.isDone());
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
                from("direct:size")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=SIZE");
                from("direct:putasync")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=PUTASYNC");
                from("direct:putallasync")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=PUTALLASYNC");
                from("direct:putifabsentasync")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=PUTIFABSENTASYNC");
                from("direct:replaceasync")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=REPLACEASYNC");
                from("direct:removeasync")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=REMOVEASYNC");
                from("direct:clearasync")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&command=CLEARASYNC");
            }
        };
    }
}
