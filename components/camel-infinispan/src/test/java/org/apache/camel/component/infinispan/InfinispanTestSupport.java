package org.apache.camel.component.infinispan;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.Before;

public class InfinispanTestSupport extends CamelTestSupport {
    protected static final String KEY_ONE = "keyOne";
    protected static final String VALUE_ONE = "valueOne";

    protected BasicCacheContainer basicCacheContainer;

    @Override
    @Before
    public void setUp() throws Exception {
        basicCacheContainer = new DefaultCacheManager();
        basicCacheContainer.start();
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        basicCacheContainer.stop();
        super.tearDown();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("cacheContainer", basicCacheContainer);
        return registry;
    }

    protected BasicCache<Object, Object> currentCache() {
        return basicCacheContainer.getCache();
    }
}
