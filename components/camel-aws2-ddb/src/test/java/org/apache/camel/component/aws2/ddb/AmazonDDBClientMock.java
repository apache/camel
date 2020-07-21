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
package org.apache.camel.component.aws2.ddb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputDescription;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateTableResponse;

public class AmazonDDBClientMock implements DynamoDbClient {
    public static final long NOW = 1327709390233L;
    DescribeTableRequest describeTableRequest;
    CreateTableRequest createTableRequest;
    UpdateTableRequest updateTableRequest;
    DeleteTableRequest deleteTableRequest;
    PutItemRequest putItemRequest;
    UpdateItemRequest updateItemRequest;
    DeleteItemRequest deleteItemRequest;
    GetItemRequest getItemRequest;
    BatchGetItemRequest batchGetItemRequest;
    ScanRequest scanRequest;
    QueryRequest queryRequest;

    public AmazonDDBClientMock() {
    }

    @Override
    public DescribeTableResponse describeTable(DescribeTableRequest describeTableRequest) {
        this.describeTableRequest = describeTableRequest;
        String tableName = describeTableRequest.tableName();
        if ("activeTable".equals(tableName)) {
            return tableWithStatus(TableStatus.ACTIVE);
        } else if ("creatibleTable".equals(tableName) && createTableRequest != null) {
            return tableWithStatus(TableStatus.ACTIVE);
        } else if ("FULL_DESCRIBE_TABLE".equals(tableName)) {
            return DescribeTableResponse.builder()
                .table(TableDescription.builder().tableName(tableName).tableStatus(TableStatus.ACTIVE).creationDateTime(Instant.now()).itemCount(100L)
                    .keySchema(KeySchemaElement.builder().attributeName("name").build())
                    .provisionedThroughput(ProvisionedThroughputDescription.builder().readCapacityUnits(20L).writeCapacityUnits(10L).build()).tableSizeBytes(1000L).build())
                .build();
        }
        AwsServiceException.Builder builder = AwsServiceException.builder();
        AwsErrorDetails.Builder builderError = AwsErrorDetails.builder();
        builderError.errorMessage("Resource not found");
        builder.awsErrorDetails(builderError.build());
        AwsServiceException ase = builder.build();
        throw ase;
    }

    private DescribeTableResponse tableWithStatus(TableStatus active) {
        return DescribeTableResponse.builder().table(TableDescription.builder().tableStatus(active).build()).build();
    }

    @Override
    public CreateTableResponse createTable(CreateTableRequest createTableRequest) {
        this.createTableRequest = createTableRequest;
        return CreateTableResponse.builder().tableDescription(TableDescription.builder().tableStatus(TableStatus.CREATING).build()).build();
    }

    @Override
    public UpdateTableResponse updateTable(UpdateTableRequest updateTableRequest) {
        this.updateTableRequest = updateTableRequest;
        return null;
    }

    @Override
    public DeleteTableResponse deleteTable(DeleteTableRequest deleteTableRequest) {
        this.deleteTableRequest = deleteTableRequest;
        return DeleteTableResponse.builder()
            .tableDescription(TableDescription.builder().provisionedThroughput(ProvisionedThroughputDescription.builder().build()).tableName(deleteTableRequest.tableName())
                .creationDateTime(Instant.now()).itemCount(10L).keySchema(new ArrayList<KeySchemaElement>()).tableSizeBytes(20L).tableStatus(TableStatus.ACTIVE).build())
            .build();
    }

    @Override
    public PutItemResponse putItem(PutItemRequest putItemRequest) {
        this.putItemRequest = putItemRequest;
        return PutItemResponse.builder().attributes(getAttributes()).build();
    }

    private Map<String, AttributeValue> getAttributes() {
        Map<String, AttributeValue> attributes = new HashMap<>();
        attributes.put("attrName", AttributeValue.builder().s("attrValue").build());
        return attributes;
    }

    @Override
    public UpdateItemResponse updateItem(UpdateItemRequest updateItemRequest) {
        this.updateItemRequest = updateItemRequest;
        return UpdateItemResponse.builder().attributes(getAttributes()).build();
    }

    @Override
    public DeleteItemResponse deleteItem(DeleteItemRequest deleteItemRequest) {
        this.deleteItemRequest = deleteItemRequest;
        return DeleteItemResponse.builder().attributes(getAttributes()).build();
    }

    @Override
    public GetItemResponse getItem(GetItemRequest getItemRequest) {
        this.getItemRequest = getItemRequest;
        return GetItemResponse.builder().item(getAttributes()).build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public BatchGetItemResponse batchGetItem(BatchGetItemRequest batchGetItemRequest) {
        this.batchGetItemRequest = batchGetItemRequest;
        Map<String, List<Map<String, AttributeValue>>> responseMap = new HashMap<>();
        List<Map<String, AttributeValue>> p = new ArrayList<>();
        p.add(getAttributes());
        responseMap.put("DOMAIN1", p);
        Map<String, AttributeValue> keysMap = new HashMap<>();
        keysMap.put("1", AttributeValue.builder().s("UNPROCESSED_KEY").build());
        Map<String, KeysAndAttributes> unprocessedKeys = new HashMap<>();
        unprocessedKeys.put("DOMAIN1", KeysAndAttributes.builder().keys(keysMap).build());

        return BatchGetItemResponse.builder().responses(responseMap).unprocessedKeys(unprocessedKeys).build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public ScanResponse scan(ScanRequest scanRequest) {
        this.scanRequest = scanRequest;
        ConsumedCapacity.Builder consumed = ConsumedCapacity.builder();
        consumed.capacityUnits(1.0);
        Map<String, AttributeValue> lastEvaluatedKey = new HashMap<>();
        lastEvaluatedKey.put("1", AttributeValue.builder().s("LAST_KEY").build());
        return ScanResponse.builder().consumedCapacity(consumed.build()).count(1).items(getAttributes()).scannedCount(10).lastEvaluatedKey(lastEvaluatedKey).build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public QueryResponse query(QueryRequest queryRequest) {
        this.queryRequest = queryRequest;
        ConsumedCapacity.Builder consumed = ConsumedCapacity.builder();
        consumed.capacityUnits(1.0);
        Map<String, AttributeValue> lastEvaluatedKey = new HashMap<>();
        lastEvaluatedKey.put("1", AttributeValue.builder().s("LAST_KEY").build());
        return QueryResponse.builder().consumedCapacity(consumed.build()).count(1).items(getAttributes()).lastEvaluatedKey(lastEvaluatedKey).build();
    }

    @Override
    public String serviceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }
}
