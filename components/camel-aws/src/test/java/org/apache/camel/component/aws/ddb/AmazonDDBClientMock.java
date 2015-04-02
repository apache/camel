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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemResult;
import com.amazonaws.services.dynamodbv2.model.ConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteItemResult;
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteTableResult;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputDescription;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.TableStatus;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateTableResult;


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
                    .withKeySchema(new KeySchemaElement().withAttributeName("name"))
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
                .withKeySchema(new ArrayList<KeySchemaElement>())
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
        Map<String, List<Map<String, AttributeValue>>> responseMap = new HashMap<String, List<Map<String, AttributeValue>>>();
        List<Map<String, AttributeValue>> p = new ArrayList<Map<String, AttributeValue>>();
        p.add(getAttributes());
        responseMap.put("DOMAIN1", p);
        Map<String, AttributeValue> keysMap = new HashMap<String, AttributeValue>();
        keysMap.put("1", new AttributeValue("UNPROCESSED_KEY"));
        Map<String, KeysAndAttributes> unprocessedKeys = new HashMap<String, KeysAndAttributes>();
        unprocessedKeys.put("DOMAIN1", new KeysAndAttributes().withKeys(keysMap));

        return new BatchGetItemResult()
                .withResponses(responseMap)
                .withUnprocessedKeys(unprocessedKeys);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ScanResult scan(ScanRequest scanRequest) {
        this.scanRequest = scanRequest;
        ConsumedCapacity consumed = new ConsumedCapacity();
        consumed.setCapacityUnits(1.0);
        Map<String, AttributeValue> lastEvaluatedKey = new HashMap<String, AttributeValue>();
        lastEvaluatedKey.put("1", new AttributeValue("LAST_KEY"));
        return new ScanResult()
                .withConsumedCapacity(consumed)
                .withCount(1)
                .withItems(getAttributes())
                .withScannedCount(10)
                .withLastEvaluatedKey(lastEvaluatedKey);
    }

    @SuppressWarnings("unchecked")
    @Override
    public QueryResult query(QueryRequest queryRequest) {
        this.queryRequest = queryRequest;
        ConsumedCapacity consumed = new ConsumedCapacity();
        consumed.setCapacityUnits(1.0);
        Map<String, AttributeValue> lastEvaluatedKey = new HashMap<String, AttributeValue>();
        lastEvaluatedKey.put("1", new AttributeValue("LAST_KEY"));
        return new QueryResult()
                .withConsumedCapacity(consumed)
                .withCount(1)
                .withItems(getAttributes())
                .withLastEvaluatedKey(lastEvaluatedKey);
    }
}
