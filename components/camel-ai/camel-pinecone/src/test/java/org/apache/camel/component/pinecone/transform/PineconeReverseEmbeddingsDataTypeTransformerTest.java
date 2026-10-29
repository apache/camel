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
package org.apache.camel.component.pinecone.transform;

import java.util.List;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.proto.QueryResponse;
import io.pinecone.proto.ScoredVector;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PineconeReverseEmbeddingsDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final PineconeReverseEmbeddingsDataTypeTransformer transformer = new PineconeReverseEmbeddingsDataTypeTransformer();

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: the matches of a query, or null, as transformerIn says, and the List of String transformerOut says
        BodyTypes bodyTypes = PineconeReverseEmbeddingsDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertArrayEquals(new Carrier[] { Carrier.LIST, Carrier.NULL }, bodyTypes.transformerIn().value());
        assertArrayEquals(new Carrier[] { Carrier.LIST }, bodyTypes.transformerOut().value());
        assertEquals("java.util.List<java.lang.String>", bodyTypes.transformerOut().javaType());

        List<ScoredVectorWithUnsignedIndices> matches = List.of(match("1", "hello"), match("2", null));
        assertEquals(List.of("hello", ""), transform(matches));

        // a null body gives an empty List
        assertEquals(List.of(), transform(null));

        // the QueryResponseWithUnsignedIndices a QUERY leaves is not a List: the transformer gives an empty List
        QueryResponseWithUnsignedIndices response = new QueryResponseWithUnsignedIndices(
                QueryResponse.newBuilder().addMatches(scoredVector("1", "hello")).build());
        assertEquals(List.of(), transform(response));
        // its matches are the body the transformer needs
        assertEquals(List.of("hello"), transform(response.getMatchesList()));
    }

    private Object transform(Object body) throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(body);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        return exchange.getMessage().getBody();
    }

    private static ScoredVectorWithUnsignedIndices match(String id, String text) {
        return new ScoredVectorWithUnsignedIndices(scoredVector(id, text));
    }

    private static ScoredVector scoredVector(String id, String text) {
        Struct.Builder metadata = Struct.newBuilder();
        if (text != null) {
            metadata.putFields("text_segment", Value.newBuilder().setStringValue(text).build());
        }
        return ScoredVector.newBuilder().setId(id).setScore(0.9f).setMetadata(metadata.build()).build();
    }
}
