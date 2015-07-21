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
        assertEquals(value.toString(), VALUE_ONE);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Integer.class), new Integer(2));
        assertNotEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Integer.class), new Integer(4));
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
        assertEquals(value.toString(), VALUE_ONE);
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
        assertEquals(value.toString(), VALUE_ONE);      
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
        assertEquals(null, resultGet);
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
        assertEquals(value.toString(), VALUE_ONE);  
        
        Thread.sleep(10000);
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(currentCache().size(), 2);
        Object value = currentCache().get(KEY_ONE);
        assertEquals(value.toString(), VALUE_ONE);
        value = currentCache().get(KEY_TWO);
        assertEquals(value.toString(), VALUE_TWO);
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

        assertEquals(currentCache().size(), 2);
        Object value = currentCache().get(KEY_ONE);
        assertEquals(value.toString(), VALUE_ONE);
        value = currentCache().get(KEY_TWO);
        assertEquals(value.toString(), VALUE_TWO);
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        Exchange exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
            }
        });
        resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(currentCache().size(), 2);
        
        Thread.sleep(10000);
        
        Exchange exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
            }
        });
        resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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
        assertEquals(currentCache().size(), 2);
        Object value = currentCache().get(KEY_ONE);
        assertEquals(value.toString(), VALUE_ONE);
        value = currentCache().get(KEY_TWO);
        assertEquals(value.toString(), VALUE_TWO);
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
        assertEquals(currentCache().size(), 2);
        Object value = currentCache().get(KEY_ONE);
        assertEquals(value.toString(), VALUE_ONE);
        value = currentCache().get(KEY_TWO);
        assertEquals(value.toString(), VALUE_TWO);
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        Exchange exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
            }
        });
        resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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
        assertEquals(currentCache().size(), 2);
        
        Thread.sleep(10000);
        
        Exchange exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
            }
        });
        resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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
        assertEquals(value.toString(), VALUE_ONE);
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
        assertEquals(value.toString(), VALUE_TWO);
        assertEquals(currentCache().size(), 2);
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
        assertEquals(value.toString(), VALUE_ONE);      
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
        assertEquals(value.toString(), VALUE_ONE);  
        
        Thread.sleep(6000);
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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
        assertEquals(value.toString(), VALUE_ONE);  
        
        Thread.sleep(10000);
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), false);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), true);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), false);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), true);
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
        assertEquals(value.toString(), VALUE_ONE);
        
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
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), "existing value");
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), VALUE_ONE);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), VALUE_ONE);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), VALUE_ONE);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), VALUE_ONE);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
        
        Thread.sleep(10000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), true);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), true);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), true);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
        
        Thread.sleep(10000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), VALUE_ONE);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), VALUE_ONE);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), VALUE_ONE);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
        
        Thread.sleep(10000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), true);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), true);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
        
        Thread.sleep(LIFESPAN_TIME * 1000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), true);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);
        
        Thread.sleep(10000);
        
        exchange = template.send("direct:get", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
            }
        });
        String resultGet = exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class);
        assertEquals(null, resultGet);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), VALUE_ONE);

        Object value = currentCache().get(KEY_ONE);
        assertEquals(value, null);
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
        assertEquals(fut.isDone(), Boolean.TRUE);

        Object value = currentCache().get(KEY_ONE);
        assertEquals(value, null);
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

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class), true);

        Object value = currentCache().get(KEY_ONE);
        assertEquals(value, null);
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
        assertEquals(fut.isDone(), Boolean.TRUE);

        Object value = currentCache().get(KEY_ONE);
        assertEquals(value, null);
    }

    @Test
    public void clearsAllValues() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        assertEquals(currentCache().isEmpty(), false);

        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.CLEAR);
            }
        });

        assertEquals(currentCache().isEmpty(), true);
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
        assertEquals(fut.isDone(), Boolean.TRUE);

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
