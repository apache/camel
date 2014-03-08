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

import java.net.URL;

import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 */
public class EHCacheUtilTest extends Assert {
    @Test
    public void testCreateCacheManagers() throws Exception {
        // no arg
        assertNotNull("create with no arg", EHCacheUtil.createCacheManager());
        
        URL configURL = EHCacheUtil.class.getResource("/test-ehcache.xml");
        assertNotNull(configURL);
        
        // string
        assertNotNull("create with string", EHCacheUtil.createCacheManager(configURL.getPath()));
        
        // url
        assertNotNull("create with url", EHCacheUtil.createCacheManager(configURL));
        
        // inputstream
        assertNotNull("create with inputstream", EHCacheUtil.createCacheManager(configURL.openStream()));
        
        // config
        Configuration conf = ConfigurationFactory.parseConfiguration(configURL);
        assertNotNull(conf);
        assertNotNull("create with configuration", EHCacheUtil.createCacheManager(conf));
    }
}
