package org.apache.camel.component.infinispan.processor.idempotent;

import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.jgroups.util.Util.assertFalse;
import static org.jgroups.util.Util.assertTrue;

public class InfinispanIdempotentRepositoryTest {
    protected BasicCacheContainer basicCacheContainer;
    protected InfinispanIdempotentRepository idempotentRepository;
    protected String cacheName = "test";
    public static final GlobalConfiguration GLOBAL_CONFIGURATION = new GlobalConfigurationBuilder().globalJmxStatistics().allowDuplicateDomains(true).build();

    @Before
    public void setUp() throws Exception {
        basicCacheContainer = new DefaultCacheManager(GLOBAL_CONFIGURATION);
        basicCacheContainer.start();
        idempotentRepository = InfinispanIdempotentRepository.infinispanIdempotentRepository(basicCacheContainer, cacheName);
    }

    @After
    public void tearDown() throws Exception {
        basicCacheContainer.stop();
    }

    @Test
    public void addsNewKeysToCache() throws Exception {
        assertTrue(idempotentRepository.add("One"));
        assertTrue(idempotentRepository.add("Two"));

        assertTrue(getCache().containsKey("One"));
        assertTrue(getCache().containsKey("Two"));
    }

    @Test
    public void skipsAddingSecondTimeTheSameKey() throws Exception {
        assertTrue(idempotentRepository.add("One"));
        assertFalse(idempotentRepository.add("One"));
    }

    @Test
    public void containsPreviouslyAddedKey() throws Exception {
        assertFalse(idempotentRepository.contains("One"));

        idempotentRepository.add("One");

        assertTrue(idempotentRepository.contains("One"));
    }

    @Test
    public void removesAnExistingKey() throws Exception {
        idempotentRepository.add("One");

        assertTrue(idempotentRepository.remove("One"));

        assertFalse(idempotentRepository.contains("One"));
    }

    @Test
    public void doesntRemoveMissingKey() throws Exception {
        assertFalse(idempotentRepository.remove("One"));
    }

    private BasicCache<Object, Object> getCache() {
        return basicCacheContainer.getCache(cacheName);
    }
}
