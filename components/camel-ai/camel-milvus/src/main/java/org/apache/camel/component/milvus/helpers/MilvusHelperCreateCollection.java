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
package org.apache.camel.component.milvus.helpers;

import io.milvus.grpc.DataType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusHeaders;

/**
 * A Camel {@link Processor} that builds a Milvus {@link io.milvus.param.collection.CreateCollectionParam} from simple
 * string properties and sets it as the exchange body together with the {@link MilvusAction#CREATE_COLLECTION} header.
 */
public class MilvusHelperCreateCollection implements Processor {

    private String collectionName = "default_collection";
    private String collectionDescription = "Default collection";
    private String idFieldName = "id";
    private String dimension = "768";
    private String textFieldName = "content";
    private String textFieldDataType = "VarChar";
    private String vectorFieldName = "embedding";
    private String vectorDataType = "FloatVector";
    private String textFieldMaxLength = "2048";
    private String additionalTextFields;

    @Override
    public void process(Exchange exchange) throws Exception {
        int vectorDim = Integer.parseInt(dimension);
        int maxLength = Integer.parseInt(textFieldMaxLength);
        DataType textDataType = DataType.valueOf(textFieldDataType);

        FieldType idField = FieldType.newBuilder()
                .withName(idFieldName)
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();

        FieldType.Builder textFieldBuilder = FieldType.newBuilder()
                .withName(textFieldName)
                .withDataType(textDataType);
        if (textDataType == DataType.VarChar) {
            textFieldBuilder.withMaxLength(maxLength);
        }
        FieldType textField = textFieldBuilder.build();

        DataType vecDataType = DataType.valueOf(vectorDataType);

        FieldType vectorField = FieldType.newBuilder()
                .withName(vectorFieldName)
                .withDataType(vecDataType)
                .withDimension(vectorDim)
                .build();

        CreateCollectionParam.Builder builder = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription(collectionDescription)
                .addFieldType(idField)
                .addFieldType(textField);

        if (additionalTextFields != null && !additionalTextFields.isBlank()) {
            for (String fieldName : additionalTextFields.split(",")) {
                String trimmed = fieldName.trim();
                if (!trimmed.isEmpty()) {
                    FieldType extraField = FieldType.newBuilder()
                            .withName(trimmed)
                            .withDataType(DataType.VarChar)
                            .withMaxLength(maxLength)
                            .build();
                    builder.addFieldType(extraField);
                }
            }
        }

        builder.addFieldType(vectorField);

        exchange.getIn().setBody(builder.build());
        exchange.getIn().setHeader(MilvusHeaders.ACTION, MilvusAction.CREATE_COLLECTION);
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getCollectionDescription() {
        return collectionDescription;
    }

    public void setCollectionDescription(String collectionDescription) {
        this.collectionDescription = collectionDescription;
    }

    public String getIdFieldName() {
        return idFieldName;
    }

    public void setIdFieldName(String idFieldName) {
        this.idFieldName = idFieldName;
    }

    public String getDimension() {
        return dimension;
    }

    /**
     * @param dimension the vector dimension as a string (e.g., {@code 768}, {@code 1536})
     */
    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getTextFieldName() {
        return textFieldName;
    }

    public void setTextFieldName(String textFieldName) {
        this.textFieldName = textFieldName;
    }

    public String getTextFieldDataType() {
        return textFieldDataType;
    }

    /**
     * @param textFieldDataType the Milvus {@link io.milvus.grpc.DataType} enum name (e.g., {@code VarChar},
     *                          {@code Int8})
     */
    public void setTextFieldDataType(String textFieldDataType) {
        this.textFieldDataType = textFieldDataType;
    }

    public String getVectorDataType() {
        return vectorDataType;
    }

    /**
     * @param vectorDataType the Milvus {@link io.milvus.grpc.DataType} enum name for the vector field (e.g.,
     *                       {@code FloatVector}, {@code BinaryVector}, {@code Float16Vector})
     */
    public void setVectorDataType(String vectorDataType) {
        this.vectorDataType = vectorDataType;
    }

    public String getVectorFieldName() {
        return vectorFieldName;
    }

    public void setVectorFieldName(String vectorFieldName) {
        this.vectorFieldName = vectorFieldName;
    }

    public String getTextFieldMaxLength() {
        return textFieldMaxLength;
    }

    /**
     * @param textFieldMaxLength the maximum length for VarChar fields as a string (e.g., {@code 2048})
     */
    public void setTextFieldMaxLength(String textFieldMaxLength) {
        this.textFieldMaxLength = textFieldMaxLength;
    }

    public String getAdditionalTextFields() {
        return additionalTextFields;
    }

    /**
     * @param additionalTextFields comma-separated list of extra VarChar field names (e.g., {@code title,author})
     */
    public void setAdditionalTextFields(String additionalTextFields) {
        this.additionalTextFields = additionalTextFields;
    }
}
