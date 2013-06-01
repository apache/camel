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
package org.apache.camel.component.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.Element;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.BaseCacheTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ReflectionHelper;
import org.junit.Test;

public class CacheProducerTest extends BaseCacheTest {
    private static final Poetry POETRY;

    private static final String FILEPATH_UPDATEDTEST_TXT = "src/test/resources/updatedtest.txt";

    private static final String FILEPATH_TEST_TXT = "src/test/resources/test.txt";

    @EndpointInject(uri = "mock:CacheProducerTest.result")
    protected MockEndpoint resultEndpoint;

    @EndpointInject(uri = "mock:CacheProducerTest.cacheException")
    protected MockEndpoint cacheExceptionEndpoint;

    static {
        POETRY = new Poetry();
        POETRY.setPoet("Ralph Waldo Emerson");
        POETRY.setPoem("Brahma");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void sendFile(final String path) throws Exception {
        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");

                // Read in the file content using the exchange charset
                byte[] fileContent = context.getTypeConverter().mandatoryConvertTo(byte[].class, exchange, new File(path));

                Message in = exchange.getIn();
                in.setBody(fileContent);
            }
        });
    }

    private void sendEmptyBody() {
        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(null);
            }
        });
    }

    private byte[] getFileAsByteArray(String path) throws Exception {
        // Read from an input stream
        InputStream is = IOHelper.buffered(new FileInputStream(path));

        byte[] buffer = IOConverter.toBytes(is);
        is.close();

        return buffer;
    }

    private void sendOriginalFile() throws Exception {
        sendFile(FILEPATH_TEST_TXT);
    }

    private void sendUpdatedFile() throws Exception {
        sendFile(FILEPATH_UPDATEDTEST_TXT);
    }

    private void sendSerializedData() throws Exception {
        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {

                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(POETRY);
            }
        });
    }

    @Test
    public void testAddingDataToCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        context.start();
        log.debug("------------Beginning CacheProducer Add Test---------------");
        sendOriginalFile();
    }

    @Test
    public void testAddingDataToCacheWithNonStringCacheKey() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant(10L)).
                        to("cache://TestCache1");
            }
        });
        context.start();
        NotifyBuilder notify = new NotifyBuilder(context).whenExactlyDone(1).create();

        log.debug("------------Beginning CacheProducer Add Test---------------");
        sendOriginalFile();

        notify.matches(10, TimeUnit.SECONDS);
        assertNotNull(fetchElement("10"));
    }

    @Test
    public void testAddingDataElementEternal() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        setHeader(CacheConstants.CACHE_ELEMENT_EXPIRY_ETERNAL, constant(Boolean.TRUE)).
                        to("cache://TestCache1");
            }
        });
        context.start();
        log.debug("------------Beginning CacheProducer Add Test---------------");
        sendOriginalFile();
        Element element = fetchElement("Ralph_Waldo_Emerson");
        assertTrue(element.isEternal());
    }

    @Test
    public void testAddingDataElementIdle() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        setHeader(CacheConstants.CACHE_ELEMENT_EXPIRY_IDLE, constant(24)).
                        to("cache://TestCache1");
            }
        });
        context.start();
        log.debug("------------Beginning CacheProducer Add Test---------------");
        sendOriginalFile();
        Element element = fetchElement("Ralph_Waldo_Emerson");
        assertEquals(24, element.getTimeToIdle());
    }

    @Test
    public void testAddingDataElementTimeToLive() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        setHeader(CacheConstants.CACHE_ELEMENT_EXPIRY_TTL, constant(42)).
                        to("cache://TestCache1");
            }
        });
        context.start();
        log.debug("------------Beginning CacheProducer Add Test---------------");
        sendOriginalFile();
        Element element = fetchElement("Ralph_Waldo_Emerson");
        assertEquals(42, element.getTimeToLive());
    }

    private Element fetchElement(String key) {
        CacheEndpoint ep = context.getEndpoint("cache://TestCache1", CacheEndpoint.class);
        Cache cache = ep.getCacheManagerFactory().getInstance().getCache("TestCache1");
        return cache.get(key);
    }

    @Test
    public void testAddingDataToCacheDoesFailOnEmptyBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        resultEndpoint.expectedMessageCount(0);
        cacheExceptionEndpoint.expectedMessageCount(1);
        context.start();
        log.debug("------------Beginning CacheProducer Add Does Fail On Empty Body Test---------------");
        sendEmptyBody();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAddingSerializableDataToCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        context.start();
        log.debug("------------Beginning CacheProducer Add Test---------------");
        sendOriginalFile();
    }

    @Test
    public void testUpdatingDataInCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_UPDATE)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        context.start();
        log.debug("------------Beginning CacheProducer Update Test---------------");
        sendSerializedData();
    }

    @Test
    public void testUpdatingDataInCacheDoesFailOnEmptyBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_UPDATE)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        cacheExceptionEndpoint.expectedMessageCount(1);
        context.start();
        log.debug("------------Beginning CacheProducer Update Does Fail On Empty Body Test---------------");
        sendEmptyBody();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testDeletingDataFromCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_DELETE)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        context.start();
        log.debug("------------Beginning CacheProducer Delete Test---------------");
        sendUpdatedFile();
    }

    @Test
    public void testDeletingDataFromCacheDoesNotFailOnEmptyBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_DELETE)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        cacheExceptionEndpoint.expectedMessageCount(0);
        context.start();
        log.debug("------------Beginning CacheProducer Delete Does Not Fail On Empty Body Test---------------");
        sendEmptyBody();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testDeletingAllDataFromCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_DELETEALL)).
                        to("cache://TestCache1");
            }
        });
        context.start();
        log.debug("------------Beginning CacheProducer Delete All Elements Test---------------");
        sendUpdatedFile();
    }

    @Test
    public void testDeletingAllDataFromCacheDoesNotFailOnEmptyBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_DELETEALL)).
                        to("cache://TestCache1");
            }
        });
        cacheExceptionEndpoint.expectedMessageCount(0);
        context.start();
        log.debug("------------Beginning CacheProducer Delete All Elements Does Not Fail On Empty Body Test---------------");
        sendEmptyBody();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testUnknownOperation() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant("UNKNOWN")).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1").
                        to("mock:CacheProducerTest.result");
            }
        });
        resultEndpoint.expectedMessageCount(0);
        cacheExceptionEndpoint.expectedMessageCount(1);
        context.start();
        log.debug("------------Beginning CacheProducer Query An Elements Test---------------");
        sendUpdatedFile();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testUnknownOperationDoesNotFailOnEmptyBody() throws Exception {
        final RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        choice().when(exceptionMessage().isEqualTo(CacheConstants.CACHE_OPERATION + " UNKNOWN is not supported.")).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException").end();

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant("UNKNOWN")).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1").
                        to("mock:CacheProducerTest.result");
            }
        };
        context.setTracing(true);
        context.addRoutes(builder);
        resultEndpoint.expectedMessageCount(0);
        cacheExceptionEndpoint.expectedMessageCount(1);
        context.start();
        log.debug("------------Beginning CacheProducer Query An Elements Does Fail On Empty Body Test---------------");
        sendEmptyBody();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testQueringNonExistingDataFromCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_DELETEALL)).
                        to("cache://TestCache1").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:CacheProducerTest.result").end();
            }
        });
        resultEndpoint.expectedMessageCount(0);
        cacheExceptionEndpoint.expectedMessageCount(0);
        context.start();
        log.debug("------------Beginning CacheProducer Query An Elements Test---------------");
        sendUpdatedFile();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testQueringNonExistingDataFromCacheDoesNotFailOnEmptyBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_DELETEALL)).
                        to("cache://TestCache1").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:CacheProducerTest.result").end();
            }
        });
        resultEndpoint.expectedMessageCount(0);
        cacheExceptionEndpoint.expectedMessageCount(0);
        context.start();
        log.debug("------------Beginning CacheProducer Query An Elements Does Not Fail On Empty Body Test---------------");
        sendEmptyBody();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testQueringDataFromCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1").
                        setBody(constant("Don't care. This body will be overridden.")).
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:CacheProducerTest.result").end();
            }
        });

        resultEndpoint.expectedMessageCount(1);
        cacheExceptionEndpoint.expectedMessageCount(0);
        String body = new String(getFileAsByteArray(FILEPATH_UPDATEDTEST_TXT), "UTF-8");
        resultEndpoint.expectedBodiesReceived(body);
        context.start();
        log.debug("------------Beginning CacheProducer Query An Elements Test---------------");
        sendUpdatedFile();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testQueringDataFromCacheUsingUrlParameters() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        to("cache://TestCache1?operation=add&key=foo").
                        setBody(constant("Don't care. This body will be overridden.")).
                        to("cache://TestCache1?operation=get&key=foo").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:CacheProducerTest.result").end();
            }
        });

        resultEndpoint.expectedMessageCount(1);
        cacheExceptionEndpoint.expectedMessageCount(0);
        resultEndpoint.expectedBodiesReceived(POETRY);
        context.start();
        log.debug("------------Beginning CacheProducer Query An Elements Test---------------");
        sendSerializedData();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testQueringDataFromCacheUsingUrlParametersMixed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        to("cache://TestCache1?key=foo").
                        setBody(constant("Don't care. This body will be overridden.")).
                        setHeader(CacheConstants.CACHE_KEY, constant("foo")).
                        to("cache://TestCache1?operation=get").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:CacheProducerTest.result").end();
            }
        });

        resultEndpoint.expectedMessageCount(1);
        cacheExceptionEndpoint.expectedMessageCount(0);
        resultEndpoint.expectedBodiesReceived(POETRY);
        context.start();
        log.debug("------------Beginning CacheProducer Query An Elements Test---------------");
        sendSerializedData();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testQueringDataFromCacheUsingUrlParametersOverrided() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("foo")).
                        to("cache://TestCache1?operation=get&key=bar").
                        setBody(constant("Don't care. This body will be overridden.")).
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET)).
                        setHeader(CacheConstants.CACHE_KEY, constant("foo")).
                        to("cache://TestCache1?operation=delete&key=Piotr_Klimczak").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:CacheProducerTest.result").end();
            }
        });

        resultEndpoint.expectedMessageCount(1);
        cacheExceptionEndpoint.expectedMessageCount(0);
        resultEndpoint.expectedBodiesReceived(POETRY);
        context.start();
        log.debug("------------Beginning CacheProducer Query An Elements Test---------------");
        sendSerializedData();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testCheckDataFromCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        setBody(constant("Test body")).
                        to("cache://TestCache1").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_URL_CHECK)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:CacheProducerTest.result").end();
            }
        });

        resultEndpoint.expectedMessageCount(1);
        cacheExceptionEndpoint.expectedMessageCount(0);
        resultEndpoint.expectedBodiesReceived("Test body");
        context.start();
        log.debug("------------Beginning CacheProducer Check An Element Exists Test---------------");
        sendOriginalFile();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testCheckDataFromCacheNegativeTest() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        setBody(constant("Test body")).
                        to("cache://TestCache1").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_URL_CHECK)).
                        setHeader(CacheConstants.CACHE_KEY, constant("foo")).
                        to("cache://TestCache1").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:CacheProducerTest.result").end();
            }
        });

        resultEndpoint.expectedMessageCount(0);
        cacheExceptionEndpoint.expectedMessageCount(0);
        context.start();
        log.debug("------------Beginning CacheProducer Check An Element Does Not Exist Test---------------");
        sendOriginalFile();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testCheckExpiredDataFromCache() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        setBody(constant("Test body")).
                        to("cache://TestCache1?timeToLiveSeconds=1");

                from("direct:b").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_URL_CHECK)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNull()).
                        to("mock:CacheProducerTest.result").end();
            }
        });

        // Put an element to the cache
        resultEndpoint.expectedMessageCount(1);
        cacheExceptionEndpoint.expectedMessageCount(0);
        context.start();
        log.debug("------------Beginning CacheProducer Check An Element Does Not Exist After Expiry Test---------------");
        sendOriginalFile();

        // simulate the cache element to appear as "expired" (without having to wait for it to happen)
        // however as there's no public API to do so we're forced to make use of the reflection API here
        Cache testCache = cache.getCacheManagerFactory().getInstance().getCache("TestCache1");
        Element element = testCache.getQuiet("Ralph_Waldo_Emerson");
        ReflectionHelper.setField(Element.class.getDeclaredField("creationTime"), element, System.currentTimeMillis() - 10000);
        testCache.putQuiet(element);

        // Check that the element is not found when expired
        template.sendBody("direct:b", "dummy");
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

}
