/**
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
package org.apache.camel.component.aws.ddb;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodb.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodb.model.AttributeValue;
import com.amazonaws.services.dynamodb.model.BatchGetItemRequest;
import com.amazonaws.services.dynamodb.model.BatchGetItemResult;
import com.amazonaws.services.dynamodb.model.BatchResponse;
import com.amazonaws.services.dynamodb.model.CreateTableRequest;
import com.amazonaws.services.dynamodb.model.CreateTableResult;
import com.amazonaws.services.dynamodb.model.DeleteItemRequest;
import com.amazonaws.services.dynamodb.model.DeleteItemResult;
import com.amazonaws.services.dynamodb.model.DeleteTableRequest;
import com.amazonaws.services.dynamodb.model.DeleteTableResult;
import com.amazonaws.services.dynamodb.model.DescribeTableRequest;
import com.amazonaws.services.dynamodb.model.DescribeTableResult;
import com.amazonaws.services.dynamodb.model.GetItemRequest;
import com.amazonaws.services.dynamodb.model.GetItemResult;
import com.amazonaws.services.dynamodb.model.Key;
import com.amazonaws.services.dynamodb.model.KeySchema;
import com.amazonaws.services.dynamodb.model.KeySchemaElement;
import com.amazonaws.services.dynamodb.model.KeysAndAttributes;
import com.amazonaws.services.dynamodb.model.ProvisionedThroughputDescription;
import com.amazonaws.services.dynamodb.model.PutItemRequest;
import com.amazonaws.services.dynamodb.model.PutItemResult;
import com.amazonaws.services.dynamodb.model.QueryRequest;
import com.amazonaws.services.dynamodb.model.QueryResult;
import com.amazonaws.services.dynamodb.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodb.model.ScanRequest;
import com.amazonaws.services.dynamodb.model.ScanResult;
import com.amazonaws.services.dynamodb.model.TableDescription;
import com.amazonaws.services.dynamodb.model.TableStatus;
import com.amazonaws.services.dynamodb.model.UpdateItemRequest;
import com.amazonaws.services.dynamodb.model.UpdateItemResult;
import com.amazonaws.services.dynamodb.model.UpdateTableRequest;
import com.amazonaws.services.dynamodb.model.UpdateTableResult;

public class AmazonDDBClientMock extends AmazonDynamoDBClient {
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
        super(new BasicAWSCredentials("user", "secret"));
    }

    @Override
    public DescribeTableResult describeTable(DescribeTableRequest describeTableRequest) {
        this.describeTableRequest = describeTableRequest;
        String tableName = describeTableRequest.getTableName();
        if ("activeTable".equals(tableName)) {
            return tableWithStatus(TableStatus.ACTIVE);
        } else if ("creatibleTable".equals(tableName) && createTableRequest != null) {
            return tableWithStatus(TableStatus.ACTIVE);
        } else if ("FULL_DESCRIBE_TABLE".equals(tableName)) {
            return new DescribeTableResult().withTable(new TableDescription()
                    .withTableName(tableName)
                    .withTableStatus(TableStatus.ACTIVE)
                    .withCreationDateTime(new Date(NOW))
                    .withItemCount(100L)
                    .withKeySchema(new KeySchema(new KeySchemaElement().withAttributeName("name")))
                    .withProvisionedThroughput(new ProvisionedThroughputDescription()
                            .withReadCapacityUnits(20L)
                            .withWriteCapacityUnits(10L))
                    .withTableSizeBytes(1000L));
        }
        throw new ResourceNotFoundException(tableName + " is missing");
    }

    private DescribeTableResult tableWithStatus(TableStatus active) {
        return new DescribeTableResult().withTable(new TableDescription().withTableStatus(active));
    }

    @Override
    public CreateTableResult createTable(CreateTableRequest createTableRequest) {
        this.createTableRequest = createTableRequest;
        return new CreateTableResult().withTableDescription(
                new TableDescription().withTableStatus(TableStatus.CREATING));
    }

    @Override
    public UpdateTableResult updateTable(UpdateTableRequest updateTableRequest) {
        this.updateTableRequest = updateTableRequest;
        return null;
    }

    @Override
    public DeleteTableResult deleteTable(DeleteTableRequest deleteTableRequest) {
        this.deleteTableRequest = deleteTableRequest;
        return new DeleteTableResult().withTableDescription(new TableDescription()
                .withProvisionedThroughput(new ProvisionedThroughputDescription())
                .withTableName(deleteTableRequest.getTableName())
                .withCreationDateTime(new Date(NOW))
                .withItemCount(10L)
                .withKeySchema(new KeySchema())
                .withTableSizeBytes(20L)
                .withTableStatus(TableStatus.ACTIVE));
    }

    @Override
    public PutItemResult putItem(PutItemRequest putItemRequest) {
        this.putItemRequest = putItemRequest;
        return new PutItemResult().withAttributes(getAttributes());
    }

    private Map<String, AttributeValue> getAttributes() {
        Map<String, AttributeValue> attributes = new HashMap<String, AttributeValue>();
        attributes.put("attrName", new AttributeValue("attrValue"));
        return attributes;
    }

    @Override
    public UpdateItemResult updateItem(UpdateItemRequest updateItemRequest) {
        this.updateItemRequest = updateItemRequest;
        return new UpdateItemResult().withAttributes(getAttributes());
    }

    @Override
    public DeleteItemResult deleteItem(DeleteItemRequest deleteItemRequest) {
        this.deleteItemRequest = deleteItemRequest;
        return new DeleteItemResult().withAttributes(getAttributes());
    }

    @Override
    public GetItemResult getItem(GetItemRequest getItemRequest) {
        this.getItemRequest = getItemRequest;
        return new GetItemResult().withItem(getAttributes());
    }

    @SuppressWarnings("unchecked")
    @Override
    public BatchGetItemResult batchGetItem(BatchGetItemRequest batchGetItemRequest) {
        this.batchGetItemRequest = batchGetItemRequest;
        Map<String, BatchResponse> responseMap = new HashMap<String, BatchResponse>();
        responseMap.put("DOMAIN1", new BatchResponse().withItems(getAttributes()));

        Map<String, KeysAndAttributes> unprocessedKeys = new HashMap<String, KeysAndAttributes>();
        unprocessedKeys.put("DOMAIN1", new KeysAndAttributes().withKeys(
                new Key(new AttributeValue("UNPROCESSED_KEY"))));

        return new BatchGetItemResult()
                .withResponses(responseMap)
                .withUnprocessedKeys(unprocessedKeys);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ScanResult scan(ScanRequest scanRequest) {
        this.scanRequest = scanRequest;
        return new ScanResult()
                .withConsumedCapacityUnits(1.0)
                .withCount(1)
                .withItems(getAttributes())
                .withScannedCount(10)
                .withLastEvaluatedKey(new Key(new AttributeValue("LAST_KEY")));
    }

    @SuppressWarnings("unchecked")
    @Override
    public QueryResult query(QueryRequest queryRequest) {
        this.queryRequest = queryRequest;
        return new QueryResult()
                .withConsumedCapacityUnits(1.0)
                .withCount(1)
                .withItems(getAttributes())
                .withLastEvaluatedKey(new Key(new AttributeValue("LAST_KEY")));
    }
}
