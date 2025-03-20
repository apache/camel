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

import org.apache.camel.CamelContext;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.api.annotations.indexing.option.VectorSimilarity;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.schema.Schema;
import org.infinispan.protostream.schema.Type;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;

import static org.apache.camel.impl.engine.DefaultComponentResolver.RESOURCE_PATH;

public final class EmbeddingStoreUtil {
    public static final String DEFAULT_TYPE_NAME_PREFIX = "CamelInfinispanRemoteEmbedding";
    public static final String FIELD_ID = "id";
    public static final String FIELD_EMBEDDING = "embedding";
    public static final String FIELD_TEXT = "TEXT";
    public static final String FIELD_METADATA_KEYS = "metadataKeys";
    public static final String FIELD_METADATA_VALUES = "metadataValues";

    private EmbeddingStoreUtil() {
        // Utility class
    }

    public static boolean isEmbeddingStoreEnabled(CamelContext context, InfinispanRemoteConfiguration configuration) {
        return configuration.isEmbeddingStoreEnabled()
                && (context.hasComponent("langchain4j-embeddings") != null || context.getCamelContextExtension()
                        .getFactoryFinder(RESOURCE_PATH)
                        .findClass("langchain4j-embeddings")
                        .isPresent());
    }

    public static String getSchema(InfinispanRemoteConfiguration configuration) {
        long dimension = configuration.getEmbeddingStoreDimension();
        VectorSimilarity vectorSimilarity = configuration.getEmbeddingStoreVectorSimilarity();
        return new Schema.Builder(getSchemeFileName(configuration))
                .addMessage(getTypeName(configuration))
                .addComment("@Indexed")
                .addField(Type.Scalar.STRING, FIELD_ID, 1)
                .addComment("@Text")
                .addRepeatedField(Type.Scalar.FLOAT, FIELD_EMBEDDING, 2)
                .addComment("@Vector(dimension=" + dimension + ", similarity=" + vectorSimilarity + ")")
                .addField(Type.Scalar.STRING, FIELD_TEXT, 3)
                .addComment("@Keyword")
                .addRepeatedField(Type.Scalar.STRING, FIELD_METADATA_KEYS, 4)
                .addRepeatedField(Type.Scalar.STRING, FIELD_METADATA_VALUES, 5)
                .build()
                .toString();
    }

    public static String getSchemeFileName(InfinispanRemoteConfiguration configuration) {
        return "%s.proto".formatted(getTypeName(configuration));
    }

    public static FileDescriptorSource getSchemaFileDescriptor(InfinispanRemoteConfiguration configuration) {
        return FileDescriptorSource.fromString(getSchemeFileName(configuration), getSchema(configuration));
    }

    public static void configureMarshaller(InfinispanRemoteConfiguration configuration, ConfigurationBuilder builder) {
        ProtoStreamMarshaller marshaller = new ProtoStreamMarshaller();
        SerializationContext serializationContext = marshaller.getSerializationContext();
        FileDescriptorSource fileDescriptorSource = getSchemaFileDescriptor(configuration);
        serializationContext.registerProtoFiles(fileDescriptorSource);
        serializationContext.registerMarshaller(new InfinispanRemoteEmbeddingMarshaller(getTypeName(configuration)));
        builder.marshaller(marshaller);
    }

    public static void registerSchema(InfinispanRemoteConfiguration configuration, RemoteCacheManager cacheContainer) {
        RemoteCache<Object, Object> metadataCache
                = cacheContainer.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
        metadataCache.put(getSchemeFileName(configuration), getSchema(configuration));
    }

    public static String getTypeName(InfinispanRemoteConfiguration configuration) {
        String embeddingStoreTypeName = configuration.getEmbeddingStoreTypeName();
        if (ObjectHelper.isEmpty(embeddingStoreTypeName)) {
            embeddingStoreTypeName = "%s%d".formatted(DEFAULT_TYPE_NAME_PREFIX, configuration.getEmbeddingStoreDimension());
        }
        return embeddingStoreTypeName;
    }
}
