package org.apache.camel.component.infinispan;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanProducer extends DefaultProducer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanProducer.class);
    private InfinispanConfiguration configuration;
    private BasicCacheContainer cacheContainer;
    private boolean isManagedCacheContainer;

    public InfinispanProducer(InfinispanEndpoint endpoint, InfinispanConfiguration configuration) {
        super(endpoint);
        this.configuration = configuration;
    }

    public void process(Exchange exchange) throws Exception {
        new InfinispanOperation(getCache(exchange)).process(exchange);
    }

    @Override
    protected void doStart() throws Exception {
        cacheContainer = configuration.getCacheContainer();
        if (cacheContainer == null) {
            cacheContainer = new RemoteCacheManager(configuration.getHost());
            cacheContainer.start();
            isManagedCacheContainer = true;
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (isManagedCacheContainer) {
            cacheContainer.stop();
        }
        super.doStop();
    }

    private BasicCache getCache(Exchange exchange) {
        String cacheName = exchange.getIn().getHeader(InfinispanConstants.CACHE_NAME, String.class);
        if (cacheName == null) {
            cacheName = configuration.getCasheName();
        }
        LOGGER.trace("Cache[{}]", cacheName);
        return cacheName != null ? cacheContainer.getCache(cacheName) : cacheContainer.getCache();
    }
}
