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
package org.apache.camel.component.langchain.embeddings;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.langchain4j.data.embedding.Embedding;
import io.milvus.param.dml.InsertParam;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;

public class LangchainEmbeddingsTestSupport {

    private LangchainEmbeddingsTestSupport() {
    }

    public static class AsInsertParam {
        @Handler
        public InsertParam AsInsertParam(Exchange e) throws InvalidProtocolBufferException {
            Embedding embedding = e.getMessage().getHeader(LangchainEmbeddings.Headers.VECTOR, Embedding.class);
            List<InsertParam.Field> fields = new ArrayList<>();
            ArrayList list = new ArrayList<>();
            list.add(embedding.vectorAsList());
            fields.add(new InsertParam.Field("vector", list));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName("embeddings")
                    .withFields(fields)
                    .build();

            return insertParam;
        }
    }
}
