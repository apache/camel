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

package org.apache.camel.component.langchain4j.core;

import java.util.HashMap;
import java.util.Map;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;

/**
 * Converter methods to convert from / to LangChain4j types.
 */
@Converter(generateLoader = true)
public class LangChain4jConverter {

    @Converter
    public static Document toDocument(String value, Exchange exchange) {
        return Document.document(value, toMetadata(exchange));
    }

    private static Metadata toMetadata(Exchange exchange) {
        if (exchange == null) {
            return new Metadata();
        }
        if (exchange.getMessage() == null) {
            return new Metadata();
        }

        Map<String, Object> metadata = null;
        Map<String, Object> headers = exchange.getMessage().getHeaders();

        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (!entry.getKey().startsWith(LangChain4j.METADATA_PREFIX)) {
                continue;
            }

            if (metadata == null) {
                metadata = new HashMap<>();
            }

            metadata.put(entry.getKey().substring(LangChain4j.METADATA_PREFIX_LEN), entry.getValue());
        }

        return metadata != null ? Metadata.from(metadata) : new Metadata();
    }
}
