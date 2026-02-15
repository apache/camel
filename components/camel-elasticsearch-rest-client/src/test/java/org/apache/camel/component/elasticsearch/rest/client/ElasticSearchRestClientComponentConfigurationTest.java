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
package org.apache.camel.component.elasticsearch.rest.client;

import java.io.IOException;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ElasticSearchRestClientComponentConfigurationTest extends CamelTestSupport {

    @Test
    void componentConfiguration() throws IOException {
        RestClient client = RestClient.builder(new HttpHost("localhost", 9200)).build();

        try {
            ElasticsearchRestClientComponent component = new ElasticsearchRestClientComponent();
            component.setHostAddressesList("localhost:9200");
            component.setUser("camel");
            component.setPassword("c4m3l");
            component.setEnableSniffer(true);
            component.setConnectionTimeout(100);
            component.setSocketTimeout(200);
            component.setSniffAfterFailureDelay(300);
            component.setSnifferInterval(400);
            component.setCertificatePath("/foo/bar");
            component.setRestClient(client);

            context.addComponent("elasticsearch-rest-client", component);

            ElasticsearchRestClientEndpoint endpoint = context.getEndpoint(
                    "elasticsearch-rest-client:camel?operation=CREATE_INDEX", ElasticsearchRestClientEndpoint.class);
            assertEquals(component.getHostAddressesList(), endpoint.getHostAddressesList());
            assertEquals(component.getUser(), endpoint.getUser());
            assertEquals(component.getPassword(), endpoint.getPassword());
            assertEquals(component.isEnableSniffer(), endpoint.isEnableSniffer());
            assertEquals(component.getConnectionTimeout(), endpoint.getConnectionTimeout());
            assertEquals(component.getSocketTimeout(), endpoint.getSocketTimeout());
            assertEquals(component.getSniffAfterFailureDelay(), endpoint.getSniffAfterFailureDelay());
            assertEquals(component.getSnifferInterval(), endpoint.getSnifferInterval());
            assertEquals(component.getCertificatePath(), endpoint.getCertificatePath());
            assertSame(component.getRestClient(), endpoint.getRestClient());
        } finally {
            client.close();
        }
    }
}
