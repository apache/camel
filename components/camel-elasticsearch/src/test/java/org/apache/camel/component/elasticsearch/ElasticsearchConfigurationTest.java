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
package org.apache.camel.component.elasticsearch;

import java.net.URI;
import java.util.Map;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.URISupport;

import org.junit.Test;

public class ElasticsearchConfigurationTest extends CamelTestSupport {

    @Test
    public void localNode() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertTrue(conf.isLocal());
        assertEquals("twitter", conf.getIndexName());
        assertEquals("tweet", conf.getIndexType());
        assertEquals("INDEX", conf.getOperation());
        assertTrue(conf.isData());
        assertNull(conf.getClusterName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void localNonDataNodeThrowsIllegalArgumentException() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet&data=false");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        new ElasticsearchConfiguration(uri, parameters);
    }

    @Test
    public void localConfDefaultsToDataNode() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertEquals("INDEX", conf.getOperation());
        assertTrue(conf.isLocal());
        assertTrue(conf.isData());
    }

    @Test
    public void clusterConfDefaultsToNonDataNode() throws Exception {
        URI uri = new URI("elasticsearch://clustername?operation=INDEX&indexName=twitter&indexType=tweet");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertEquals("clustername", conf.getClusterName());
        assertEquals("INDEX", conf.getOperation());
        assertFalse(conf.isLocal());
        assertFalse(conf.isData());
    }
    
    @Test
    public void clusterConfWithIpAddress() throws Exception {
        URI uri = new URI("elasticsearch://clustername?operation=INDEX&indexName=twitter&indexType=tweet&ip=127.0.0.1");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertEquals("clustername", conf.getClusterName());
        assertEquals("INDEX", conf.getOperation());
        assertFalse(conf.isLocal());
        assertFalse(conf.isData());
        assertEquals("127.0.0.1", conf.getIp());
        assertEquals(9300, conf.getPort().intValue());
    }

    @Test
    public void localDataNode() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet&data=true");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertTrue(conf.isLocal());
        assertEquals("INDEX", conf.getOperation());
        assertEquals("twitter", conf.getIndexName());
        assertEquals("tweet", conf.getIndexType());
        assertTrue(conf.isData());
        assertNull(conf.getClusterName());
    }

}
