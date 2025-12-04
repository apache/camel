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

package org.apache.camel.component.langchain4j.embeddings;

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Converter;

/**
 * Converter methods to convert from / to LangChain4j embedding types.
 */
@Converter(generateLoader = true)
public class LangChain4jEmbeddingsConverter {

    @Converter
    public static TextSegment toTextSegment(String value) {
        return TextSegment.from(value);
    }

    @Converter
    public static Embedding toEmbedding(float[] value) {
        return Embedding.from(value);
    }

    @Converter
    public static Embedding toEmbedding(List<Float> value) {
        return Embedding.from(value);
    }
}
