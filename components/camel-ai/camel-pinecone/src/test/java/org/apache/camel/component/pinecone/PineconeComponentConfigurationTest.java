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
package org.apache.camel.component.pinecone;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PineconeComponentConfigurationTest extends CamelTestSupport {

    public Properties loadPineconePropertiesFile() throws IOException {
        final String fileName = "pinecone_index.properties";
        return TestSupport.loadExternalProperties(PineconeComponentConfigurationTest.class.getClassLoader(), fileName);
    }

    @Test
    void createEndpointWithMinimalConfiguration() throws Exception {
        PineconeVectorDbComponent component = context.getComponent("pinecone", PineconeVectorDbComponent.class);
        PineconeVectorDbEndpoint endpoint = (PineconeVectorDbEndpoint) component
                .createEndpoint(
                        "pinecone://test-collection?indexName=test-serverless-index&collectionSimilarityMetric=cosine&collectionDimension=3&cloud=aws&cloudRegion=us-east-1");
        assertEquals("test-serverless-index", endpoint.getConfiguration().getIndexName());
        assertEquals("cosine", endpoint.getConfiguration().getCollectionSimilarityMetric());
        assertEquals(3, endpoint.getConfiguration().getCollectionDimension());
        assertEquals("aws", endpoint.getConfiguration().getCloud());
        assertEquals("us-east-1", endpoint.getConfiguration().getCloudRegion());
    }

    @Test
    void createEndpointWithProperties() throws Exception {
        Properties properties = loadPineconePropertiesFile();
        Map<String, Object> propsMap = (Map) properties;

        PineconeVectorDbComponent component = context.getComponent("pinecone", PineconeVectorDbComponent.class);
        PineconeVectorDbEndpoint endpoint = (PineconeVectorDbEndpoint) component
                .createEndpoint(
                        "pinecone://test-collection", "", propsMap);
        assertEquals("test-serverless-index", endpoint.getConfiguration().getIndexName());
        assertEquals("cosine", endpoint.getConfiguration().getCollectionSimilarityMetric());
        assertEquals(3, endpoint.getConfiguration().getCollectionDimension());
        assertEquals("aws", endpoint.getConfiguration().getCloud());
        assertEquals("us-east-1", endpoint.getConfiguration().getCloudRegion());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(9080, endpoint.getConfiguration().getProxyPort().intValue());
        assertEquals(false, endpoint.getConfiguration().isTls());
        assertEquals("http://www.foobar.com", endpoint.getConfiguration().getHost());
    }

}
