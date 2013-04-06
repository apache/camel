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
package org.apache.camel.component;

import org.apache.camel.component.cache.CacheComponent;
import org.apache.camel.component.cache.FileCacheManagerFactory;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;

/**
 *
 */
public class BaseCacheTest extends CamelTestSupport {

    protected CacheComponent cache;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        // use a file cache manager factory to load out test configuration
        cache = new CacheComponent();
        cache.setCacheManagerFactory(new FileCacheManagerFactory("src/test/resources/test-ehcache.xml"));
        jndi.bind("cache", cache);

        return jndi;
    }
}
