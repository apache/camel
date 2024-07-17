/*
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
package org.apache.camel.component.solr;

import java.util.stream.Stream;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudHttp2SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.LBHttp2SolrClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InitSolrEndpointTest extends SolrTestSupport {

    private String solrUrl = "solr://localhost:" + getPort() + "/solr";

    @Test
    public void endpointCreatedCorrectlyWithAllOptions() {
        SolrClient httpSolrClient = new Http2SolrClient.Builder(solrUrl).build();
        context.getRegistry().bind("client", httpSolrClient);
        SolrEndpoint solrEndpoint = context.getEndpoint(solrUrl + getFullOptions(), SolrEndpoint.class);
        assertEquals(5, solrEndpoint.getSolrConfiguration().getStreamingQueueSize(), "queue size incorrect");
        assertEquals(1, solrEndpoint.getSolrConfiguration().getStreamingThreadCount(), "thread count incorrect");
        assertNotNull(solrEndpoint);
    }

    @Test
    public void streamingEndpointCreatedCorrectly() {
        SolrEndpoint solrEndpoint = context.getEndpoint(solrUrl, SolrEndpoint.class);
        assertNotNull(solrEndpoint);
        assertEquals(SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE, solrEndpoint.getSolrConfiguration().getStreamingQueueSize(),
                "queue size incorrect");
        assertEquals(SolrConstants.DEFAULT_STREAMING_THREAD_COUNT,
                solrEndpoint.getSolrConfiguration().getStreamingThreadCount(),
                "thread count incorrect");
    }

    @Test
    public void wrongURLFormatFailsEndpointCreation() {
        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("solr://localhost:x99/solr"));
    }

    @ParameterizedTest
    @EnumSource(SolrConfiguration.SolrScheme.class)
    public void endpointWithSolrAndHttpClient(SolrConfiguration.SolrScheme solrScheme) throws Exception {
        String solrClientBaseUrl = "http://localhost:8983/solr/collection1";
        String solrEndpointUri = solrClientBaseUrl.replace("http://", solrScheme.getUri());
        // solrClient
        SolrClient httpSolrClient = new Http2SolrClient.Builder(solrClientBaseUrl).build();
        context.getRegistry().bind("client", httpSolrClient);
        SolrEndpoint solrEndpoint = context.getEndpoint(solrEndpointUri.concat("?solrClient=#client"), SolrEndpoint.class);
        assertNotNull(solrEndpoint.getSolrConfiguration().getSolrClient());
        assertEquals(httpSolrClient, solrEndpoint.getSolrConfiguration().getSolrClient());
    }

    @ParameterizedTest
    @MethodSource("provideSolrEndpointStringsWithExpectedSolrClientClass")
    public void endpointCreatedMatchesExpectedSolrClient(
            String endpointUri, Class expectedSolrClientClass, String expectedZkChroot)
            throws Exception {
        SolrComponent solrComponent = context.getComponent("solr", SolrComponent.class);
        SolrEndpoint solrEndpoint = context.getEndpoint(endpointUri, SolrEndpoint.class);
        SolrClient solrClient = solrComponent.getSolrClient((SolrProducer) solrEndpoint.createProducer(),
                solrEndpoint.getSolrConfiguration());
        assertNotNull(solrClient);
        assertEquals(expectedSolrClientClass, solrClient.getClass());
        assertEquals(expectedZkChroot, solrEndpoint.getSolrConfiguration().getZkChroot());
    }

    private static Stream<Arguments> provideSolrEndpointStringsWithExpectedSolrClientClass() {
        return Stream.of(
                Arguments.of("solr:localhost:8983/solr", Http2SolrClient.class, null),
                Arguments.of("solr://localhost:8983/solr", Http2SolrClient.class, null),
                // note: zkChroot will not be used but we can't get it from the client directly, so we need to set it
                Arguments.of("solr://localhost:2181/solr?zkChroot=/mytest", CloudHttp2SolrClient.class, "/mytest"),
                Arguments.of("solr://localhost:8983/solr,localhost:8984/solr,localhost:8985/solr", LBHttp2SolrClient.class,
                        null),
                Arguments.of("solr://localhost:8983/solr?zkHost=zk1:2181", CloudHttp2SolrClient.class, null),
                Arguments.of("solr://localhost:8983/solr?zkHost=zk1:2181,zk2:2181,zk3:2181/mytest", CloudHttp2SolrClient.class,
                        "/mytest"),
                Arguments.of("solr://localhost:8983/solr?zkHost=zk1:2181,zk2:2181,zk3:2181/mytest&zkChroot=/myZkChroot",
                        CloudHttp2SolrClient.class, "/myZkChroot"),
                Arguments.of("solrCloud:zk1:2181,zk2:2181,zk3:2181", CloudHttp2SolrClient.class, null),
                Arguments.of("solrCloud:zk1:2181,zk2:2181,zk3:2181/myZkChroot", CloudHttp2SolrClient.class, "/myZkChroot"),
                Arguments.of("solrCloud:zk1,zk2,zk3/myZkChroot", CloudHttp2SolrClient.class, "/myZkChroot"));
    }

    private String getFullOptions() {
        return "?streamingQueueSize=5&streamingThreadCount=1"
               + "&maxRetries=1&idleTimeout=100&connectionTimeout=100"
               + "&defaultMaxConnectionsPerHost=100&maxTotalConnections=100"
               + "&followRedirects=false&allowCompression=true"
               + "&requestHandler=/update"
               + "&solrClient=#client&zkChroot=/test"
               + "&username=solr&password=SolrRocks";
    }

}
