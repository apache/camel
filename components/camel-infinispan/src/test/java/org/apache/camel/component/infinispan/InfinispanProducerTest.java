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
import org.apache.camel.component.infinispan.util.Condition;
import org.infinispan.Cache;
import org.infinispan.commons.util.concurrent.NotifyingFuture;
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
                NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
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
                NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
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
                NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_ALL);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_ALL);
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT_ALL);
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
                NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
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
                NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
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
                NotifyingFuture resultPutAsync = exchange.getIn().getHeader(InfinispanConstants.RESULT, NotifyingFuture.class);
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
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

        waitForNullValue(KEY_ONE);
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
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
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.REPLACE);
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        assertEquals(exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class), VALUE_ONE);
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

        assertEquals(VALUE_ONE, exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class));
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
                exchange.getIn().setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT, TimeUnit.MILLISECONDS.toString());
            }
        });

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
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

        assertTrue(exchange.getIn().getHeader(InfinispanConstants.RESULT, Boolean.class));
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
    
    @Test
    public void statsOperation() throws Exception {
        ((Cache) currentCache()).getAdvancedCache().getStats().setStatisticsEnabled(true); 
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
        
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, KEY_TWO);
                exchange.getIn().setHeader(InfinispanConstants.VALUE, VALUE_TWO);
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.PUT);
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
        Stats resultStats = exchange.getIn().getHeader(InfinispanConstants.RESULT, Stats.class);
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
                    .to("infinispan?cacheContainer=#cacheContainer&command=PUT");
                from("direct:putifabsent")
                    .to("infinispan?cacheContainer=#cacheContainer&command=PUTIFABSENT");
                from("direct:get")
                    .to("infinispan?cacheContainer=#cacheContainer&command=GET");
                from("direct:remove")
                    .to("infinispan?cacheContainer=#cacheContainer&command=REMOVE");
                from("direct:clear")
                    .to("infinispan?cacheContainer=#cacheContainer&command=CLEAR");
                from("direct:replace")
                    .to("infinispan?cacheContainer=#cacheContainer&command=REPLACE");
                from("direct:containskey")
                    .to("infinispan?cacheContainer=#cacheContainer&command=CONTAINSKEY");
                from("direct:containsvalue")
                    .to("infinispan?cacheContainer=#cacheContainer&command=CONTAINSVALUE");
                from("direct:size")
                    .to("infinispan?cacheContainer=#cacheContainer&command=SIZE");
                from("direct:putasync")
                    .to("infinispan?cacheContainer=#cacheContainer&command=PUTASYNC");
                from("direct:putallasync")
                    .to("infinispan?cacheContainer=#cacheContainer&command=PUTALLASYNC");
                from("direct:putifabsentasync")
                    .to("infinispan?cacheContainer=#cacheContainer&command=PUTIFABSENTASYNC");
                from("direct:replaceasync")
                    .to("infinispan?cacheContainer=#cacheContainer&command=REPLACEASYNC");
                from("direct:removeasync")
                    .to("infinispan?cacheContainer=#cacheContainer&command=REMOVEASYNC");
                from("direct:clearasync")
                    .to("infinispan?cacheContainer=#cacheContainer&command=CLEARASYNC");
                from("direct:stats")
                    .to("infinispan?cacheContainer=#cacheContainer&command=STATS");
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
                return exchange.getIn().getHeader(InfinispanConstants.RESULT, String.class) == null;
            }
        }, 1000);
    }
}
