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
package org.apache.camel.component.cbor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CBORUseDefaultMapperWithNonCBORMapperInRegistryTest extends CamelTestSupport {

    // The bytes obtained when marshaling an Author with the fallback object mapper created in CBORDataFormat.doInit()
    private static final byte[] AUTHOR_CBOR_BYTES = new byte[] {
            -65, 100, 110, 97, 109, 101, 99, 68, 111, 110, 103, 115, 117, 114, 110, 97, 109, 101, 103, 87, 105, 110, 115, 108,
            111, 119, -1 };

    @Test
    void unmarshalShouldIgnoreTheNonCBORMapperFromRegistry() {
        Author authReturned = template.requestBody("direct:unmarshal-author", AUTHOR_CBOR_BYTES, Author.class);
        assertEquals("Don", authReturned.getName());
        assertEquals("Winslow", authReturned.getSurname());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {

                // Bind a non CBOR mapper in the registry, it should be ignored as it can't unmarshal a CBOR payload
                context.getRegistry().bind("non-cbor-object-mapper", new ObjectMapper());

                CBORDataFormat useDefaultObjectMapperDataFormat = new CBORDataFormat();
                useDefaultObjectMapperDataFormat.setUnmarshalType(Author.class);
                from("direct:unmarshal-author").unmarshal(useDefaultObjectMapperDataFormat);
            }
        };
    }

}
