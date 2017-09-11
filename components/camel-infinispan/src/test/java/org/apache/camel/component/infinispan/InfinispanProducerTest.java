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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.util.Condition;
import org.infinispan.Cache;
import org.infinispan.stats.Stats;
import org.junit.Test;

import static org.apache.camel.component.infinispan.util.Wait.waitFor;


public class InfinispanProducerTest extends InfinispanTestSupport {

    private static final String COMMAND_VALUE = "commandValue";
    private static final String COMMAND_KEY = "commandKey1";
    private static final long LIFESPAN_TIME = 100;
    private static final long LIFESPAN_FOR_MAX_IDLE = -1;
    private static final long MAX_IDLE_TIME = 200;

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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.SIZE);
            }
        });

        Integer cacheSize = exchange.getIn().getBody(Integer.class);
        assertEquals(cacheSize, new Integer(2));
    }

    @Test
    public void publishKeyAndValueByExplicitlySpecifyingTheOperation() throws Exception {
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT);
            }
        });

        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
    }

    @Test
    public void publishKeyAndValueAsync() throws Exception {
        final Exchange exchange = template.send("direct:putasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
            }
        });

        waitFor(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                CompletableFuture<Object> resultPutAsync = exchange.getIn().getBody(CompletableFuture.class);
                Object value = currentCache().get(KEY_ONE);
                return resultPutAsync.isDone() && value.toString().equals(VALUE_ONE);
            }
        }, 5000);
    }

    @Test
    public void publishKeyAndValueAsyncWithLifespan() throws Exception {
        final Exchange exchange = template.send("direct:putasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        waitFor(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                CompletableFuture<Object> resultPutAsync = exchange.getIn().getBody(CompletableFuture.class);
                Object value = currentCache().get(KEY_ONE);
                return resultPutAsync.isDone() && value.equals(VALUE_ONE);
            }
        }, 1000);

        waitForNullValue(KEY_ONE);
    }

    @Test
    public void publishKeyAndValueAsyncWithLifespanAndMaxIdle() throws Exception {
        final Exchange exchange = template.send("direct:putasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        waitFor(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                CompletableFuture<Object> resultPutAsync = exchange.getIn().getBody(CompletableFuture.class);
                return resultPutAsync.isDone() && currentCache().get(KEY_ONE).toString().equals(VALUE_ONE);
            }
        }, 1000);

        Thread.sleep(300);
        waitForNullValue(KEY_ONE);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALL);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALL);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        assertEquals(2, currentCache().size());
        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        value = currentCache().get(KEY_TWO);
        assertEquals(VALUE_TWO, value.toString());

        waitForNullValue(KEY_ONE);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALL);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        assertEquals(2, currentCache().size());

        Thread.sleep(300);
        waitForNullValue(KEY_TWO);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALL);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        waitFor(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                Object valueOne = currentCache().get(KEY_ONE);
                Object valueTwo = currentCache().get(KEY_TWO);
                return valueOne.equals(VALUE_ONE) && valueTwo.equals(VALUE_TWO) && currentCache().size() == 2;
            }
        }, 100);

        waitForNullValue(KEY_ONE);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTALL);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        waitFor(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                return currentCache().size() == 2;
            }
        }, 100);

        Thread.sleep(300);

        waitForNullValue(KEY_ONE);
        waitForNullValue(KEY_TWO);
    }

    @Test
    public void putIfAbsentAlreadyExists() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        template.send("direct:putifabsent", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTIFABSENT);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUTIFABSENT);
            }
        });

        Object value = currentCache().get(KEY_TWO);
        assertEquals(VALUE_TWO, value.toString());
        assertEquals(2, currentCache().size());
    }

    @Test
    public void putIfAbsentKeyAndValueAsync() throws Exception {
        final Exchange exchange = template.send("direct:putifabsentasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
            }
        });
        waitFor(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                CompletableFuture<Object> resultPutAsync = exchange.getIn().getBody(CompletableFuture.class);
                return resultPutAsync.isDone() && currentCache().get(KEY_ONE).equals(VALUE_ONE);
            }
        }, 2000);
    }

    @Test
    public void putIfAbsentKeyAndValueAsyncWithLifespan() throws Exception {
        final Exchange exchange = template.send("direct:putifabsentasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_TIME));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        waitFor(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                CompletableFuture<Object> resultPutAsync = exchange.getIn().getBody(CompletableFuture.class);
                return resultPutAsync.isDone() && currentCache().get(KEY_ONE).equals(VALUE_ONE);
            }
        }, 100);

        waitForNullValue(KEY_ONE);
    }

    @Test
    public void putIfAbsentKeyAndValueAsyncWithLifespanAndMaxIdle() throws Exception {
        final Exchange exchange = template.send("direct:putifabsentasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME, new Long(LIFESPAN_FOR_MAX_IDLE));
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        waitFor(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                CompletableFuture<Object> resultPutAsync = exchange.getIn().getBody(CompletableFuture.class);
                return resultPutAsync.isDone() && currentCache().get(KEY_ONE).equals(VALUE_ONE);
            }
        }, 500);

        Thread.sleep(300);
        waitForNullValue(KEY_ONE);
    }

    @Test
    public void notContainsKeyTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:containskey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.CONTAINSKEY);
            }
        });

        Boolean cacheContainsKey = exchange.getIn().getBody(Boolean.class);
        assertFalse(cacheContainsKey);
    }

    @Test
    public void containsKeyTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:containskey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.CONTAINSKEY);
            }
        });

        Boolean cacheContainsKey = exchange.getIn().getBody(Boolean.class);
        assertTrue(cacheContainsKey);
    }

    @Test
    public void notContainsValueTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:containsvalue", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.CONTAINSVALUE);
            }
        });

        Boolean cacheContainsValue = exchange.getIn().getBody(Boolean.class);
        assertFalse(cacheContainsValue);
    }

    @Test
    public void containsValueTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:containsvalue", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.CONTAINSVALUE);
            }
        });

        Boolean cacheContainsValue = exchange.getIn().getBody(Boolean.class);
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT);
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
        String resultGet = exchange.getIn().getBody(String.class);
        assertEquals(VALUE_ONE, resultGet);

        waitForNullValue(KEY_ONE);
    }
    
    @Test
    public void getOrDefault() throws Exception {
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT);
            }
        });

        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());

        Exchange exchange;
        exchange = template.send("direct:getOrDefault", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.DEFAULT_VALUE, "defaultTest");
            }
        });
        String resultGet = exchange.getIn().getBody(String.class);
        assertEquals(VALUE_ONE, resultGet);
        
        exchange = template.send("direct:getOrDefault", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
                exchange.getIn().setHeader(InfinispanConstants.DEFAULT_VALUE, "defaultTest");
            }
        });
        resultGet = exchange.getIn().getBody(String.class);
        assertEquals("defaultTest", resultGet);
    }

    @Test
    public void putOperationReturnsThePreviousValue() throws Exception {
        currentCache().put(KEY_ONE, "existing value");

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT);
            }
        });

        String result = exchange.getIn().getBody(String.class);
        assertEquals("existing value", result);
    }

    @Test
    public void retrievesAValueByKey() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.GET);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getBody(String.class));
    }

    @Test
    public void replaceAValueByKey() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:replace", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getBody(String.class));
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getBody(String.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));

        waitForNullValue(KEY_ONE);
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getBody(String.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));

        Thread.sleep(300);
        waitForNullValue(KEY_ONE);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE);
            }
        });

        assertTrue(exchange.getIn().getBody(Boolean.class));
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE);
            }
        });

        assertTrue(exchange.getIn().getBody(Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));

        waitForNullValue(KEY_ONE);
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE);
            }
        });

        assertTrue(exchange.getIn().getBody(Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));

        Thread.sleep(300);
        waitForNullValue(KEY_ONE);
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

        assertEquals(VALUE_ONE, exchange.getIn().getBody(String.class));
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        assertEquals(exchange.getIn().getBody(String.class), VALUE_ONE);
        assertEquals(currentCache().get(KEY_ONE), VALUE_TWO);

        waitForNullValue(KEY_ONE);
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getBody(String.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));

        Thread.sleep(300);
        waitForNullValue(KEY_ONE);
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

        assertTrue(exchange.getIn().getBody(Boolean.class));
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        assertTrue(exchange.getIn().getBody(Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));

        waitForNullValue(KEY_ONE);
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME, new Long(MAX_IDLE_TIME));
                exchange.getIn().setHeader(InfinispanConstants.MAX_IDLE_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        assertTrue(exchange.getIn().getBody(Boolean.class));
        assertEquals(VALUE_TWO, currentCache().get(KEY_ONE));

        Thread.sleep(300);
        waitForNullValue(KEY_ONE);
    }

    @Test
    public void deletesExistingValueByKey() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVE);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getBody(String.class));

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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVEASYNC);
            }
        });

        Thread.sleep(100);
        CompletableFuture<Object> fut = exchange.getIn().getBody(CompletableFuture.class);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVE);
            }
        });

        assertTrue(exchange.getIn().getBody(Boolean.class));

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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVEASYNC);
            }
        });

        Thread.sleep(100);
        CompletableFuture<Object> fut = exchange.getIn().getBody(CompletableFuture.class);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.CLEAR);
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
        String resultGet = exchange.getIn().getBody(String.class);
        assertEquals(COMMAND_VALUE, resultGet);

        exchange = template.send("direct:remove", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, COMMAND_KEY);
            }
        });
        String resultRemove = exchange.getIn().getBody(String.class);
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
    public void testDeprecatedUriOption() throws Exception {
        template.send("direct:put-deprecated-option", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, COMMAND_KEY);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, COMMAND_VALUE);
            }
        });
        String result = (String) currentCache().get(COMMAND_KEY);
        assertEquals(COMMAND_VALUE, result);
        assertEquals(COMMAND_VALUE, currentCache().get(COMMAND_KEY));
    }

    @Test
    public void testDeprecatedUriCommand() throws Exception {
        template.send("direct:put-deprecated-command", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, COMMAND_KEY);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, COMMAND_VALUE);
            }
        });
        String result = (String) currentCache().get(COMMAND_KEY);
        assertEquals(COMMAND_VALUE, result);
        assertEquals(COMMAND_VALUE, currentCache().get(COMMAND_KEY));
    }

    @Test
    public void clearAsyncTest() throws Exception {
        currentCache().put(KEY_ONE, VALUE_ONE);
        currentCache().put(KEY_TWO, VALUE_TWO);

        Exchange exchange = template.request("direct:clearasync", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.CLEARASYNC);
            }
        });

        Thread.sleep(100);
        CompletableFuture<Object> fut = exchange.getIn().getBody(CompletableFuture.class);
        assertTrue(fut.isDone());
        assertTrue(currentCache().isEmpty());
    }
    
    @Test
    public void statsOperation() throws Exception {
        ((Cache) currentCache()).getAdvancedCache().getStats().setStatisticsEnabled(true); 
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_ONE);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_ONE);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT);
            }
        });

        Object value = currentCache().get(KEY_ONE);
        assertEquals(VALUE_ONE, value.toString());
        
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT);
            }
        });

        value = currentCache().get(KEY_TWO);
        assertEquals(VALUE_TWO, value.toString());
        
        Exchange exchange;
        exchange = template.send("direct:stats", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
            }
        });
        Stats resultStats = exchange.getIn().getBody(Stats.class);
        assertEquals(2L, resultStats.getTotalNumberOfEntries());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                    .to("infinispan?cacheContainer=#cacheContainer");
                from("direct:put")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=PUT");
                from("direct:put-deprecated-option")
                    .to("infinispan?cacheContainer=#cacheContainer&command=PUT");
                from("direct:put-deprecated-command")
                    .to("infinispan?cacheContainer=#cacheContainer&command=CamelInfinispanOperationPut");
                from("direct:putifabsent")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=PUTIFABSENT");
                from("direct:get")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=GET");
                from("direct:getOrDefault")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=GETORDEFAULT");
                from("direct:remove")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=REMOVE");
                from("direct:clear")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=CLEAR");
                from("direct:replace")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=REPLACE");
                from("direct:containskey")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=CONTAINSKEY");
                from("direct:containsvalue")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=CONTAINSVALUE");
                from("direct:size")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=SIZE");
                from("direct:putasync")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=PUTASYNC");
                from("direct:putallasync")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=PUTALLASYNC");
                from("direct:putifabsentasync")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=PUTIFABSENTASYNC");
                from("direct:replaceasync")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=REPLACEASYNC");
                from("direct:removeasync")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=REMOVEASYNC");
                from("direct:clearasync")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=CLEARASYNC");
                from("direct:stats")
                    .to("infinispan?cacheContainer=#cacheContainer&operation=STATS");
            }
        };
    }

    private void waitForNullValue(final String key) {
        waitFor(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
                Exchange exchange = template.send("direct:get", new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setHeader(InfinispanConstants.KEY, key);
                    }
                });
                return exchange.getIn().getBody(String.class) == null;
            }
        }, 1000);
    }
}
