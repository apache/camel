package org.apache.camel.component.infinispan;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanConsumer extends DefaultConsumer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanProducer.class);
    private final InfinispanConfiguration configuration;
    private final InfinispanSyncEventListener listener;
    private DefaultCacheManager defaultCacheManager;

    public InfinispanConsumer(InfinispanEndpoint endpoint, Processor processor, InfinispanConfiguration configuration) {
        super(endpoint, processor);
        this.configuration = configuration;
        if (configuration.isSync()) {
            listener = new InfinispanSyncEventListener(this, configuration.getEventTypes());
        } else {
            listener = new InfinispanAsyncEventListener(this, configuration.getEventTypes());
        }
    }

    public void processEvent(String eventType, boolean isPre, String cacheName, Object key) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getOut().setHeader(InfinispanConstants.EVENT_TYPE, eventType);
        exchange.getOut().setHeader(InfinispanConstants.IS_PRE, isPre);
        exchange.getOut().setHeader(InfinispanConstants.CACHE_NAME, cacheName);
        exchange.getOut().setHeader(InfinispanConstants.KEY, key);

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            LOGGER.error("Error processing event ", e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.getCacheContainer() instanceof DefaultCacheManager) {
            defaultCacheManager = (DefaultCacheManager) configuration.getCacheContainer();
            Cache<Object, Object> cache;
            if (configuration.getCasheName() != null) {
                cache = defaultCacheManager.getCache(configuration.getCasheName());
            } else {
                cache = defaultCacheManager.getCache();
            }
            cache.addListener(listener);
        } else {
            throw new UnsupportedOperationException("Consumer not support for CacheContainer: " + configuration.getCacheContainer());
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (defaultCacheManager != null) {
            defaultCacheManager.removeListener(listener);
        }
        super.doStop();
    }
}
