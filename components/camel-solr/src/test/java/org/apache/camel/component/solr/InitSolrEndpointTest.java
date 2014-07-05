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
package org.apache.camel.component.solr;

import org.apache.camel.ResolveEndpointFailedException;
import org.junit.Test;

public class InitSolrEndpointTest extends SolrTestSupport {

    private String solrUrl = "solr://localhost:" + getPort() + "/solr";

    @Test
    public void endpointCreatedCorrectlyWithAllOptions() throws Exception {
        SolrEndpoint solrEndpoint = context.getEndpoint(solrUrl + getFullOptions(), SolrEndpoint.class);
        assertEquals("queue size incorrect", 5, solrEndpoint.getStreamingQueueSize());
        assertEquals("thread count incorrect", 1, solrEndpoint.getStreamingThreadCount());
        assertNotNull(solrEndpoint);
    }

    @Test
    public void streamingEndpointCreatedCorrectly() throws Exception {
        SolrEndpoint solrEndpoint = context.getEndpoint(solrUrl, SolrEndpoint.class);
        assertNotNull(solrEndpoint);
        assertEquals("queue size incorrect", SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE, solrEndpoint.getStreamingQueueSize());
        assertEquals("thread count incorrect", SolrConstants.DEFAULT_STREAMING_THREAD_COUNT, solrEndpoint.getStreamingThreadCount());
    }

    @Test(expected = ResolveEndpointFailedException.class)
    public void wrongURLFormatFailsEndpointCreation() throws Exception {
        context.getEndpoint("solr://localhost:x99/solr");
    }

    private String getFullOptions() {
        return "?streamingQueueSize=5&streamingThreadCount=1"
                + "&maxRetries=1&soTimeout=100&connectionTimeout=100"
                + "&defaultMaxConnectionsPerHost=100&maxTotalConnections=100"
                + "&followRedirects=false&allowCompression=true"
                + "&requestHandler=/update";
    }
}
