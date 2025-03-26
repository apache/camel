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
package org.apache.camel.component.infinispan.remote.embeddingstore;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class InfinispanRemoteEmbedding {
    private final String id;
    private final float[] embedding;
    private final String text;
    private final List<String> metadataKeys;
    private final List<String> metadataValues;

    public InfinispanRemoteEmbedding(String id, float[] embedding, String text, List<String> metadataKeys,
                                     List<String> metadataValues) {
        this.id = id;
        this.embedding = embedding;
        this.text = text;
        this.metadataKeys = metadataKeys;
        this.metadataValues = metadataValues;
    }

    public String getId() {
        return id;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public String getText() {
        return text;
    }

    public List<String> getMetadataKeys() {
        return metadataKeys;
    }

    public List<String> getMetadataValues() {
        return metadataValues;
    }

    @Override
    public String toString() {
        return "InfinispanRemoteEmbedding{" +
               "id='" + id + '\'' +
               ", embedding=" + Arrays.toString(embedding) +
               ", text='" + text + '\'' +
               ", metadataKeys=" + metadataKeys +
               ", metadataValues=" + metadataValues +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InfinispanRemoteEmbedding that = (InfinispanRemoteEmbedding) o;
        return Objects.equals(id, that.id) && Arrays.equals(embedding, that.embedding) && Objects.equals(text,
                that.text) && Objects.equals(metadataKeys, that.metadataKeys)
                && Objects.equals(metadataValues,
                        that.metadataValues);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, text, metadataKeys, metadataValues);
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }
}
