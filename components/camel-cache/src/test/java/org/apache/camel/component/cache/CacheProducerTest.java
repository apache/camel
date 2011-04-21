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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import net.sf.ehcache.CacheException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheProducerTest extends CamelTestSupport {

    private static final String FILEPATH_UPDATEDTEST_TXT = "./src/test/resources/updatedtest.txt";

    private static final String FILEPATH_TEST_TXT = "./src/test/resources/test.txt";

    private static final transient Logger LOG = LoggerFactory.getLogger(CacheProducerTest.class);

    @EndpointInject(uri = "mock:CacheProducerTest.result")
    protected MockEndpoint resultEndpoint;

    @EndpointInject(uri = "mock:CacheProducerTest.cacheException")
    protected MockEndpoint cacheExceptionEndpoint;

    @EndpointInject(uri = "mock:CacheProducerTest.noTypeConversionAvailableException")
    private MockEndpoint noTypeConversionAvailableExceptionEndpoint;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void sendFile(final String path) throws Exception {
        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // Read from an input stream
                InputStream is = new BufferedInputStream(
                        new FileInputStream(path));    // "./src/test/resources/test.txt"));

                byte buffer[] = IOConverter.toBytes(is);
                is.close();

                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(buffer);
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
        InputStream is = new BufferedInputStream(new FileInputStream(path));

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
                Poetry p = new Poetry();
                p.setPoet("Ralph Waldo Emerson");
                p.setPoem("Brahma");

                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(p);
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
        LOG.debug("------------Beginning CacheProducer Add Test---------------");
        sendOriginalFile();
    }

    @Test
    public void testAddingDataToCacheDoesFailOnEmptyBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:*** LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        cacheExceptionEndpoint.expectedMessageCount(1);
        context.start();
        LOG.debug("------------Beginning CacheProducer Add Does Fail On Empty Body Test---------------");
        sendEmptyBody();
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
        LOG.debug("------------Beginning CacheProducer Add Test---------------");
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
        LOG.debug("------------Beginning CacheProducer Update Test---------------");
        sendSerializedData();
    }

    @Test
    public void testUpdatingDataInCacheDoesFailOnEmptyBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:*** LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_UPDATE)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        cacheExceptionEndpoint.expectedMessageCount(1);
        context.start();
        LOG.debug("------------Beginning CacheProducer Update Does Fail On Empty Body Test---------------");
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
        LOG.debug("------------Beginning CacheProducer Delete Test---------------");
        sendUpdatedFile();
    }

    @Test
    public void testDeletingDataFromCacheDoesNotFailOnEmptyBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:*** LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_DELETE)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1");
            }
        });
        cacheExceptionEndpoint.expectedMessageCount(0);
        context.start();
        LOG.debug("------------Beginning CacheProducer Delete Does Not Fail On Empty Body Test---------------");
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
        LOG.debug("------------Beginning CacheProducer Delete All Elements Test---------------");
        sendUpdatedFile();
    }

    @Test
    public void testDeletingAllDataFromCacheDoesNotFailOnEmptyBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:*** LOGGER").
                        to("mock:CacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_DELETEALL)).
                        to("cache://TestCache1");
            }
        });
        cacheExceptionEndpoint.expectedMessageCount(0);
        context.start();
        LOG.debug("------------Beginning CacheProducer Delete All Elements Does Not Fail On Empty Body Test---------------");
        sendEmptyBody();
        cacheExceptionEndpoint.assertIsSatisfied();
    }

    @Test
    public void testUnknownOperation() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(CacheException.class).
                        handled(true).
                        to("log:*** LOGGER").
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
        LOG.debug("------------Beginning CacheProducer Query An Elements Test---------------");
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
                        to("log:*** LOGGER").
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
        LOG.debug("------------Beginning CacheProducer Query An Elements Does Fail On Empty Body Test---------------");
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
                        to("log:*** LOGGER").
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
        LOG.debug("------------Beginning CacheProducer Query An Elements Test---------------");
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
                        to("log:*** LOGGER").
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
        LOG.debug("------------Beginning CacheProducer Query An Elements Does Not Fail On Empty Body Test---------------");
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
                        to("log:*** LOGGER").
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
        LOG.debug("------------Beginning CacheProducer Query An Elements Test---------------");
        sendUpdatedFile();
        resultEndpoint.assertIsSatisfied();
        cacheExceptionEndpoint.assertIsSatisfied();
    }
}
