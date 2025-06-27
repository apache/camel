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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.infinispan.protostream.MessageMarshaller;

import static org.apache.camel.component.infinispan.remote.embeddingstore.EmbeddingStoreUtil.FIELD_EMBEDDING;
import static org.apache.camel.component.infinispan.remote.embeddingstore.EmbeddingStoreUtil.FIELD_ID;
import static org.apache.camel.component.infinispan.remote.embeddingstore.EmbeddingStoreUtil.FIELD_METADATA_KEYS;
import static org.apache.camel.component.infinispan.remote.embeddingstore.EmbeddingStoreUtil.FIELD_METADATA_VALUES;
import static org.apache.camel.component.infinispan.remote.embeddingstore.EmbeddingStoreUtil.FIELD_TEXT;

public class InfinispanRemoteEmbeddingMarshaller implements MessageMarshaller<InfinispanRemoteEmbedding> {
    private final String typeName;

    public InfinispanRemoteEmbeddingMarshaller(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public InfinispanRemoteEmbedding readFrom(ProtoStreamReader reader) throws IOException {
        String id = reader.readString(FIELD_ID);
        float[] embedding = reader.readFloats(FIELD_EMBEDDING);
        String text = reader.readString(FIELD_TEXT);
        List<String> metadataKeys = reader.readCollection(FIELD_METADATA_KEYS, new ArrayList<>(), String.class);
        List<String> metadataValues = reader.readCollection(FIELD_METADATA_VALUES, new ArrayList<>(), String.class);
        return new InfinispanRemoteEmbedding(id, embedding, text, metadataKeys, metadataValues);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, InfinispanRemoteEmbedding item) throws IOException {
        writer.writeString(FIELD_ID, item.getId());
        writer.writeFloats(FIELD_EMBEDDING, item.getEmbedding());
        writer.writeString(FIELD_TEXT, item.getText());
        writer.writeCollection(FIELD_METADATA_KEYS, item.getMetadataKeys(), String.class);
        writer.writeCollection(FIELD_METADATA_VALUES, item.getMetadataValues(), String.class);
    }

    @Override
    public Class<? extends InfinispanRemoteEmbedding> getJavaClass() {
        return InfinispanRemoteEmbedding.class;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }
}
