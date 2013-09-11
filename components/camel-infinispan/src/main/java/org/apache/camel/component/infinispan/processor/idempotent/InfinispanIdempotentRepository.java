package org.apache.camel.component.infinispan.processor.idempotent;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.manager.DefaultCacheManager;

@ManagedResource(description = "Infinispan based message id repository")
public class InfinispanIdempotentRepository extends ServiceSupport implements IdempotentRepository<Object> {
    private final String cacheName;
    private final BasicCacheContainer cacheContainer;
    private boolean isManagedCacheContainer;

    public InfinispanIdempotentRepository(BasicCacheContainer cacheContainer, String cacheName) {
        this.cacheContainer = cacheContainer;
        this.cacheName = cacheName;
    }

    public InfinispanIdempotentRepository(String cacheName) {
        cacheContainer = new DefaultCacheManager();
        this.cacheName = cacheName;
        isManagedCacheContainer = true;
    }

    public InfinispanIdempotentRepository() {
        this(null);
    }

    public static InfinispanIdempotentRepository infinispanIdempotentRepository(
            BasicCacheContainer cacheContainer, String processorName) {
        return new InfinispanIdempotentRepository(cacheContainer, processorName);
    }

    public static InfinispanIdempotentRepository infinispanIdempotentRepository(String processorName) {
        return new InfinispanIdempotentRepository(processorName);
    }

    public static InfinispanIdempotentRepository infinispanIdempotentRepository() {
        return new InfinispanIdempotentRepository();
    }

    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(Object key) {
        Boolean put = getCache().put(key, true);
        return put == null;
    }

    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(Object key) {
        return getCache().containsKey(key);
    }

    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(Object key) {
        return getCache().remove(key) != null;
    }

    @ManagedAttribute(description = "The processor name")
    public String getCacheName() {
        return cacheName;
    }

    public boolean confirm(Object key) {
        return true;
    }

    protected void doStart() throws Exception {
        // noop
    }

    protected void doStop() throws Exception {
        // noop
    }

    protected void doShutdown() throws Exception {
        super.doShutdown();
        if (isManagedCacheContainer) {
            cacheContainer.stop();
        }
    }

    private BasicCache<Object, Boolean> getCache() {
        return cacheName != null
                ? cacheContainer.<Object, Boolean>getCache(cacheName)
                : cacheContainer.<Object, Boolean>getCache();
    }
}

