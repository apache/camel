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

import org.apache.camel.util.URISupport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ElasticsearchConfigurationTest {

    @Test
    public void localNode() throws Exception {
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration();
        URI uri = new URI("elasticsearch://local?indexName=twitter&indexType=tweet");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        conf.parseURI(uri, parameters, null);
        assertTrue(conf.isLocal());
        assertEquals("twitter", conf.getIndexName());
        assertEquals("tweet", conf.getIndexType());
        assertTrue(conf.isData());
        assertNull(conf.getClusterName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void localNonDataNodeThrowsIllegalArgumentException() throws Exception {
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration();
        URI uri = new URI("elasticsearch://local?indexName=twitter&indexType=tweet&data=false");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        conf.parseURI(uri, parameters, null);
    }

    @Test
    public void localConfDefaultsToDataNode() throws Exception {
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration();
        URI uri = new URI("elasticsearch://local?indexName=twitter&indexType=tweet");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        conf.parseURI(uri, parameters, null);
        assertTrue(conf.isLocal());
        assertTrue(conf.isData());
    }

    @Test
    public void clusterConfDefaultsToNonDataNode() throws Exception {
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration();
        URI uri = new URI("elasticsearch://clustername?indexName=twitter&indexType=tweet");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        conf.parseURI(uri, parameters, null);
        assertEquals("clustername", conf.getClusterName());
        assertFalse(conf.isLocal());
        assertFalse(conf.isData());
    }

    @Test
    public void localDataNode() throws Exception {
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration();
        URI uri = new URI("elasticsearch://local?indexName=twitter&indexType=tweet&data=true");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        conf.parseURI(uri, parameters, null);
        assertTrue(conf.isLocal());
        assertEquals("twitter", conf.getIndexName());
        assertEquals("tweet", conf.getIndexType());
        assertTrue(conf.isData());
        assertNull(conf.getClusterName());
    }

}
