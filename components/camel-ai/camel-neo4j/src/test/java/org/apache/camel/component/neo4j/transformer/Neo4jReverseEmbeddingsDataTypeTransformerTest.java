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
package org.apache.camel.component.neo4j.transformer;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Neo4jReverseEmbeddingsDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final Neo4jReverseEmbeddingsDataTypeTransformer transformer = new Neo4jReverseEmbeddingsDataTypeTransformer();

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: the rows of a similarity search, as transformerIn says, and the List of String transformerOut says
        BodyTypes bodyTypes = Neo4jReverseEmbeddingsDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertArrayEquals(new Carrier[] { Carrier.LIST }, bodyTypes.transformerIn().value());
        assertArrayEquals(new Carrier[] { Carrier.LIST }, bodyTypes.transformerOut().value());
        assertEquals("java.util.List<java.lang.String>", bodyTypes.transformerOut().javaType());

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(List.of(Map.of("id", "1", "text", "hello", "score", 0.9), Map.of("id", "2")));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertEquals(List.of("hello", ""), exchange.getMessage().getBody());

        // a null body fails
        Exchange empty = new DefaultExchange(camelContext);
        assertThrows(NullPointerException.class,
                () -> transformer.transform(empty.getMessage(), DataType.ANY, DataType.ANY));
    }
}
