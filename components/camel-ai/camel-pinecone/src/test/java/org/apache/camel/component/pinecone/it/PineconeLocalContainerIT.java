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

package org.apache.camel.component.pinecone.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.camel.Exchange;
import org.apache.camel.component.pinecone.PineconeTestSupport;
import org.apache.camel.component.pinecone.PineconeVectorDbAction;
import org.apache.camel.component.pinecone.PineconeVectorDbHeaders;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperties({
    @DisabledIfSystemProperty(named = "pinecone.token", matches = ".*", disabledReason = "Pinecone token not provided"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PineconeLocalContainerIT extends PineconeTestSupport {

    @Test
    @Order(1)
    public void createServerlessIndex() {

        Exchange result = fluentTemplate
                .to("pinecone:test-collection?token=pclocal")
                .withHeader(PineconeVectorDbHeaders.ACTION, PineconeVectorDbAction.CREATE_SERVERLESS_INDEX)
                .withBody("hello")
                .withHeader(PineconeVectorDbHeaders.INDEX_NAME, "test-serverless-index")
                .withHeader(PineconeVectorDbHeaders.COLLECTION_SIMILARITY_METRIC, "cosine")
                .withHeader(PineconeVectorDbHeaders.COLLECTION_DIMENSION, 3)
                .withHeader(PineconeVectorDbHeaders.COLLECTION_CLOUD, "aws")
                .withHeader(PineconeVectorDbHeaders.COLLECTION_CLOUD_REGION, "us-east-1")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    // TODO : add more test cases once this works
    // https://github.com/pinecone-io/pinecone-dotnet-client/issues/41

    @Test
    @Order(6)
    public void deleteIndex() {

        Exchange result = fluentTemplate
                .to("pinecone:test-collection?token=pclocal")
                .withHeader(PineconeVectorDbHeaders.ACTION, PineconeVectorDbAction.DELETE_INDEX)
                .withBody("test")
                .withHeader(PineconeVectorDbHeaders.INDEX_NAME, "test-serverless-index")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }
}
