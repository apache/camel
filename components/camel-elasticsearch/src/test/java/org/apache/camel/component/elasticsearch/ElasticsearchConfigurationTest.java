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
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.support.replication.ReplicationType;

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

    @Test
    public void writeConsistencyLevelDefaultConfTest() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertTrue(conf.isLocal());
        assertEquals("INDEX", conf.getOperation());
        assertEquals("twitter", conf.getIndexName());
        assertEquals("tweet", conf.getIndexType());
        assertEquals(WriteConsistencyLevel.DEFAULT, conf.getConsistencyLevel());
        assertNull(conf.getClusterName());
    }

    @Test
    public void writeConsistencyLevelConfTest() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet&consistencyLevel=QUORUM");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertTrue(conf.isLocal());
        assertEquals("INDEX", conf.getOperation());
        assertEquals("twitter", conf.getIndexName());
        assertEquals("tweet", conf.getIndexType());
        assertEquals(WriteConsistencyLevel.QUORUM, conf.getConsistencyLevel());
        assertNull(conf.getClusterName());
    }

    @Test
    public void replicationTypeConfTest() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet&replicationType=ASYNC");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertDefaultConfigurationParameters(conf);
        assertEquals(ReplicationType.ASYNC, conf.getReplicationType());
    }

    @Test
    public void replicationTypeDefaultConfTest() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertDefaultConfigurationParameters(conf);
        assertEquals(ReplicationType.DEFAULT, conf.getReplicationType());
    }

    @Test
    public void transportAddressesSimpleHostnameTest() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&" +
                "indexType=tweet&transportAddresses=127.0.0.1");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertDefaultConfigurationParameters(conf);
        assertEquals(1, conf.getTransportAddresses().size());
        assertEquals("127.0.0.1", conf.getTransportAddresses().get(0).address().getHostString());
        assertEquals(9300, conf.getTransportAddresses().get(0).address().getPort());
    }

    @Test
    public void transportAddressesMultipleHostnameTest() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&" +
                "indexType=tweet&transportAddresses=127.0.0.1,127.0.0.2");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertDefaultConfigurationParameters(conf);
        assertEquals(2, conf.getTransportAddresses().size());
        assertEquals("127.0.0.1", conf.getTransportAddresses().get(0).address().getHostString());
        assertEquals(9300, conf.getTransportAddresses().get(0).address().getPort());
        assertEquals("127.0.0.2", conf.getTransportAddresses().get(1).address().getHostString());
        assertEquals(9300, conf.getTransportAddresses().get(1).address().getPort());
    }

    @Test
    public void transportAddressesSimpleHostnameAndPortTest() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&" +
                "indexType=tweet&transportAddresses=127.0.0.1:9305");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertDefaultConfigurationParameters(conf);
        assertEquals(1, conf.getTransportAddresses().size());
        assertEquals("127.0.0.1", conf.getTransportAddresses().get(0).address().getHostString());
        assertEquals(9305, conf.getTransportAddresses().get(0).address().getPort());
    }

    @Test
    public void transportAddressesMultipleHostnameAndPortTest() throws Exception {
        URI uri = new URI("elasticsearch://local?operation=INDEX&indexName=twitter&" +
                "indexType=tweet&transportAddresses=127.0.0.1:9400,127.0.0.2,127.0.0.3:9401");
        Map<String, Object> parameters = URISupport.parseParameters(uri);
        ElasticsearchConfiguration conf = new ElasticsearchConfiguration(uri, parameters);
        assertDefaultConfigurationParameters(conf);
        assertEquals(3, conf.getTransportAddresses().size());
        assertEquals("127.0.0.1", conf.getTransportAddresses().get(0).address().getHostString());
        assertEquals(9400, conf.getTransportAddresses().get(0).address().getPort());
        assertEquals("127.0.0.2", conf.getTransportAddresses().get(1).address().getHostString());
        assertEquals(9300, conf.getTransportAddresses().get(1).address().getPort());
        assertEquals("127.0.0.3", conf.getTransportAddresses().get(2).address().getHostString());
        assertEquals(9401, conf.getTransportAddresses().get(2).address().getPort());
    }

    private void assertDefaultConfigurationParameters(ElasticsearchConfiguration conf) {
        assertTrue(conf.isLocal());
        assertEquals("INDEX", conf.getOperation());
        assertEquals("twitter", conf.getIndexName());
        assertEquals("tweet", conf.getIndexType());
        assertNull(conf.getClusterName());
    }

}
