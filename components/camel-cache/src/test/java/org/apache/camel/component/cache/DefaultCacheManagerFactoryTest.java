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

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;

/**
 * 
 */
public class DefaultCacheManagerFactoryTest extends Assert {

    @Test
    public void testEHCacheCompatiblity() throws Exception {
        // get the default cache manager
        CacheManagerFactory factory = new DefaultCacheManagerFactory();
        CacheManager manager = factory.getInstance();
        assertEquals(Status.STATUS_ALIVE, manager.getStatus());
        
        // create another unrelated cache manager
        Configuration conf = 
            ConfigurationFactory.parseConfiguration(DefaultCacheManagerFactory.class.getResource("/test-ehcache.xml"));
        assertNotNull(conf);
        conf.setName("otherCache");
        CacheManager other = CacheManager.create(conf);
        assertEquals(Status.STATUS_ALIVE, other.getStatus());
        
        // shutdown this unrelated cache manager 
        other.shutdown();
        assertEquals(Status.STATUS_SHUTDOWN, other.getStatus());
        
        // the default cache manager should be still running
        assertEquals(Status.STATUS_ALIVE, manager.getStatus());
        
        factory.doStop();
        // the default cache manger is shutdown
        assertEquals(Status.STATUS_SHUTDOWN, manager.getStatus());
    }

    @Test
    public void testNoProvidedConfiguration() throws Exception {
        CacheManagerFactory factory = new DefaultCacheManagerFactory(getClass().getResourceAsStream("/ehcache.xml"), "/ehcache.xml");
        CacheManager manager = factory.getInstance();
        // CAMEL-7195
        assertThat("There should be no peer providers configured", manager.getCacheManagerPeerProviders().size(), is(0));
        assertThat("There should be no /ehcache.xml resource by default", getClass().getResourceAsStream("/ehcache.xml"), nullValue());
    }

    @Test
    public void testFailSafeEHCacheManager() throws Exception {
        CacheManagerFactory factory1 = new DefaultCacheManagerFactory(null, null);
        CacheManagerFactory factory2 = new DefaultCacheManagerFactory();
        assertSame("The cache managers should be the same, loaded from fallback ehcache config",
            factory1.getInstance(), factory2.getInstance());
    }

}
