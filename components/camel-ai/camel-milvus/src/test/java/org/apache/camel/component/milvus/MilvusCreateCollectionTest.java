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

package org.apache.camel.component.milvus;

import static org.assertj.core.api.Assertions.assertThat;

import io.milvus.grpc.DataType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class MilvusCreateCollectionTest extends MilvusTestSupport {

    @DisplayName("Tests that trying to create a collection without passing the action name triggers a failure")
    @Test
    public void createCollectionWithoutRequiredParameters() {
        FieldType fieldType1 = FieldType.newBuilder()
                .withName("userID")
                .withDescription("user identification")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();

        FieldType fieldType2 = FieldType.newBuilder()
                .withName("userFace")
                .withDescription("face embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(64)
                .build();

        FieldType fieldType3 = FieldType.newBuilder()
                .withName("userAge")
                .withDescription("user age")
                .withDataType(DataType.Int8)
                .build();

        CreateCollectionParam createCollectionReq = CreateCollectionParam.newBuilder()
                .withCollectionName("test")
                .withDescription("customer info")
                .withShardsNum(2)
                .withEnableDynamicField(false)
                .addFieldType(fieldType1)
                .addFieldType(fieldType2)
                .addFieldType(fieldType3)
                .build();

        Exchange result = fluentTemplate
                .to("milvus:createCollection")
                .withBody(createCollectionReq)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
    }
}
