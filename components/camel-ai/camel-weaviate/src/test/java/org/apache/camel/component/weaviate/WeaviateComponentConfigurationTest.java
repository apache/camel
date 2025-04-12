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
package org.apache.camel.component.weaviate;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WeaviateComponentConfigurationTest extends CamelTestSupport {

    public Properties loadWeaviatePropertiesFile() throws IOException {
        final String fileName = "weaviate.properties";
        return TestSupport.loadExternalProperties(WeaviateComponentConfigurationTest.class.getClassLoader(), fileName);
    }

    @Test
    void createEndpointWithMinimalConfiguration() throws Exception {
        WeaviateVectorDbComponent component = context.getComponent("weaviate", WeaviateVectorDbComponent.class);
        WeaviateVectorDbEndpoint endpoint = (WeaviateVectorDbEndpoint) component
                .createEndpoint(
                        "weaviate://test-collection?host=localhost:7979&scheme=http&proxyHost=localhost&proxyPort=7777&proxyScheme=https&apiKey=foobar123");
        assertEquals("localhost:7979", endpoint.getConfiguration().getHost());
        assertEquals("http", endpoint.getConfiguration().getScheme());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(7777, endpoint.getConfiguration().getProxyPort());
        assertEquals("https", endpoint.getConfiguration().getProxyScheme());
        assertEquals("foobar123", endpoint.getConfiguration().getApiKey());
    }

    @Test
    void createEndpointWithProperties() throws Exception {
        Properties properties = loadWeaviatePropertiesFile();
        Map<String, Object> propsMap = (Map) properties;

        WeaviateVectorDbComponent component = context.getComponent("weaviate", WeaviateVectorDbComponent.class);
        WeaviateVectorDbEndpoint endpoint = (WeaviateVectorDbEndpoint) component
                .createEndpoint(
                        "weaviate://test-collection", "", propsMap);
        assertEquals("bighost:7878", endpoint.getConfiguration().getHost());
        assertEquals("ftp", endpoint.getConfiguration().getScheme());
        assertEquals("littlehost", endpoint.getConfiguration().getProxyHost());
        assertEquals(8888, endpoint.getConfiguration().getProxyPort());
        assertEquals("ftps", endpoint.getConfiguration().getProxyScheme());
        assertEquals("barfoo123", endpoint.getConfiguration().getApiKey());
    }

}
