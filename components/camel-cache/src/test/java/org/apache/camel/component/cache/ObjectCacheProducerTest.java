package org.apache.camel.component.cache;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ObjectCacheProducerTest extends CamelTestSupport {


//    protected String ehcacheConfigurationPath() {
//        return "src/test/resources/test-object-ehcache.xml";
//    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @EndpointInject(uri = "mock:ObjectCacheProducerTest.result")
    protected MockEndpoint resultEndpoint;

    @EndpointInject(uri = "mock:ObjectCacheProducerTest.cacheException")
    protected MockEndpoint cacheExceptionEndpoint;

    /**
     * Test storing 3 elements into object cache then retrieving them back.
     * We allow cache to store maximum of 2 values to check that overflow to disk not happened (it is not
     * allowed in ehcache object cache (not serializable cache)).
     *
     * @throws Exception
     * @see net.sf.ehcache.Element for information about object cache operations in ehcache
     */
    @Test
    public void testAddingMultipleDataInCacheAndGettingBack() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                onException(Exception.class).
                        handled(true).
                        to("log:LOGGER").
                        to("mock:ObjectCacheProducerTest.cacheException");

                from("direct:a").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1?objectCache=true&overflowToDisk=false&diskPersistent=false&maxElementsInMemory=2");
                from("direct:aGet").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson")).
                        to("cache://TestCache1?objectCache=true&overflowToDisk=false&diskPersistent=false&maxElementsInMemory=2").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:ObjectCacheProducerTest.result").end();

                from("direct:b").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson_2")).
                        to("cache://TestCache1?objectCache=true&overflowToDisk=false&diskPersistent=false&maxElementsInMemory=2");
                from("direct:bGet").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson_2")).
                        to("cache://TestCache1?objectCache=true&overflowToDisk=false&diskPersistent=false&maxElementsInMemory=2").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:ObjectCacheProducerTest.result").end();

                from("direct:c").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson_3")).
                        to("cache://TestCache1?objectCache=true&overflowToDisk=false&diskPersistent=false&maxElementsInMemory=2");
                from("direct:cGet").
                        setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET)).
                        setHeader(CacheConstants.CACHE_KEY, constant("Ralph_Waldo_Emerson_3")).
                        to("cache://TestCache1?objectCache=true&overflowToDisk=false&diskPersistent=false&maxElementsInMemory=2").
                        choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNotNull()).
                        to("mock:ObjectCacheProducerTest.result").end();
            }
        });
        context.setTracing(true);
        context.start();
        resultEndpoint.expectedMessageCount(2);
        cacheExceptionEndpoint.expectedMessageCount(0);
        log.debug("------------Beginning CacheProducer Add and Get Test---------------");

        log.debug("Putting data into cache");
        sendNonSerializedData("direct:a", newPoetry("Ralph Waldo Emerson", "Brahma"));
        sendNonSerializedData("direct:b", newPoetry("Ralph Waldo Emerson", "The Rhodora"));
        sendNonSerializedData("direct:c", newPoetry("Ralph Waldo Emerson", "Concord Hymn"));

        log.debug("Retrieving data from cache");
        sendEmptyBody("direct:aGet");
        sendEmptyBody("direct:bGet");
        sendEmptyBody("direct:cGet");

        cacheExceptionEndpoint.assertIsSatisfied();
        resultEndpoint.assertIsSatisfied();
    }

    private void sendNonSerializedData(String endpoint, final PoetryNotSerializable notSerializable) throws Exception {
        template.send(endpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {

                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(notSerializable);
            }
        });
    }

    private void sendEmptyBody(String endpoint) {
        template.send(endpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(null);
            }
        });
    }

    private PoetryNotSerializable newPoetry(String poet, String poem) {
        PoetryNotSerializable poetry = new PoetryNotSerializable();
        poetry.setPoet(poet);
        poetry.setPoem(poem);
        return poetry;

    }
}
