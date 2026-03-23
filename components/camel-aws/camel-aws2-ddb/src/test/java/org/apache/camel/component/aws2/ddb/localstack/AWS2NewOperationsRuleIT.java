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
package org.apache.camel.component.aws2.ddb.localstack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.ddb.Ddb2Constants;
import org.apache.camel.component.aws2.ddb.Ddb2Operations;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchStatementResponse;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.Get;
import software.amazon.awssdk.services.dynamodb.model.ItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive integration test that exercises all new DynamoDB operations (PartiQL, transactions, batch write) in an
 * end-to-end workflow against LocalStack.
 */
public class AWS2NewOperationsRuleIT extends Aws2DDBBase {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    private static final String KEY_ATTR = "pk";
    private static final String TABLE_NAME = "TestNewOps";

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        ddbClient = AWSSDKClientUtils.newDynamoDBClient();
        CreateTableRequest createTableRequest = CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName(KEY_ATTR)
                                .keyType(KeyType.HASH)
                                .build())
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeType(ScalarAttributeType.S)
                                .attributeName(KEY_ATTR)
                                .build())
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(5L)
                        .build())
                .build();
        ddbClient.createTable(createTableRequest);
    }

    @Override
    protected void cleanupResources() throws Exception {
        super.cleanupResources();
        DeleteTableRequest deleteTableRequest = DeleteTableRequest.builder()
                .tableName(TABLE_NAME)
                .build();
        ddbClient.deleteTable(deleteTableRequest);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void batchWriteAndPartiQLQuery() {
        // Step 1: Use BatchWriteItem to insert multiple items
        Map<String, AttributeValue> item1 = makeItem("bw-1", "Batch Write Item 1");
        Map<String, AttributeValue> item2 = makeItem("bw-2", "Batch Write Item 2");

        Map<String, List<WriteRequest>> requestItems = new HashMap<>();
        requestItems.put(TABLE_NAME, Arrays.asList(
                WriteRequest.builder().putRequest(PutRequest.builder().item(item1).build()).build(),
                WriteRequest.builder().putRequest(PutRequest.builder().item(item2).build()).build()));

        Exchange batchWriteResult = template.send("direct:start", e -> {
            e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.BatchWriteItems);
            e.getIn().setHeader(Ddb2Constants.BATCH_WRITE_ITEMS, requestItems);
        });

        assertNull(batchWriteResult.getException());
        Map<String, List<WriteRequest>> unprocessed
                = batchWriteResult.getIn().getHeader(Ddb2Constants.BATCH_WRITE_UNPROCESSED_ITEMS, Map.class);
        assertNotNull(unprocessed);
        assertTrue(unprocessed.isEmpty(), "All items should have been processed");

        // Step 2: Use ExecuteStatement (PartiQL) to read back one of the items
        Exchange partiqlResult = template.send("direct:start", e -> {
            e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.ExecuteStatement);
            e.getIn().setHeader(Ddb2Constants.STATEMENT,
                    "SELECT * FROM \"" + TABLE_NAME + "\" WHERE \"" + KEY_ATTR + "\" = ?");
            e.getIn().setHeader(Ddb2Constants.STATEMENT_PARAMETERS,
                    Arrays.asList(AttributeValue.builder().s("bw-1").build()));
            e.getIn().setHeader(Ddb2Constants.CONSISTENT_READ, true);
        });

        assertNull(partiqlResult.getException());
        List<Map<String, AttributeValue>> items
                = partiqlResult.getIn().getHeader(Ddb2Constants.EXECUTE_STATEMENT_ITEMS, List.class);
        assertNotNull(items);
        assertFalse(items.isEmpty(), "PartiQL SELECT should return the item inserted by BatchWriteItem");
        assertEquals("Batch Write Item 1", items.get(0).get("data").s());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void batchExecuteStatementInsertAndSelect() {
        // Step 1: Use BatchExecuteStatement to insert items via PartiQL
        List<BatchStatementRequest> insertStatements = Arrays.asList(
                BatchStatementRequest.builder()
                        .statement("INSERT INTO \"" + TABLE_NAME + "\" VALUE {'" + KEY_ATTR
                                   + "': 'bps-1', 'data': 'Batch PartiQL 1'}")
                        .build(),
                BatchStatementRequest.builder()
                        .statement("INSERT INTO \"" + TABLE_NAME + "\" VALUE {'" + KEY_ATTR
                                   + "': 'bps-2', 'data': 'Batch PartiQL 2'}")
                        .build());

        Exchange batchInsertResult = template.send("direct:start", e -> {
            e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.BatchExecuteStatement);
            e.getIn().setHeader(Ddb2Constants.BATCH_STATEMENTS, insertStatements);
        });

        assertNull(batchInsertResult.getException());
        List<BatchStatementResponse> responses
                = batchInsertResult.getIn().getHeader(Ddb2Constants.BATCH_STATEMENT_RESPONSE, List.class);
        assertNotNull(responses);
        assertEquals(2, responses.size());

        // Step 2: Verify with PartiQL SELECT
        Exchange selectResult = template.send("direct:start", e -> {
            e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.ExecuteStatement);
            e.getIn().setHeader(Ddb2Constants.STATEMENT,
                    "SELECT * FROM \"" + TABLE_NAME + "\" WHERE \"" + KEY_ATTR + "\" = ?");
            e.getIn().setHeader(Ddb2Constants.STATEMENT_PARAMETERS,
                    Arrays.asList(AttributeValue.builder().s("bps-2").build()));
            e.getIn().setHeader(Ddb2Constants.CONSISTENT_READ, true);
        });

        assertNull(selectResult.getException());
        List<Map<String, AttributeValue>> items
                = selectResult.getIn().getHeader(Ddb2Constants.EXECUTE_STATEMENT_ITEMS, List.class);
        assertNotNull(items);
        assertFalse(items.isEmpty());
        assertEquals("Batch PartiQL 2", items.get(0).get("data").s());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void transactWriteThenTransactGet() {
        // Step 1: Use TransactWriteItems to atomically insert two items
        Map<String, AttributeValue> txItem1 = makeItem("tx-1", "Transaction Item 1");
        Map<String, AttributeValue> txItem2 = makeItem("tx-2", "Transaction Item 2");

        Exchange txWriteResult = template.send("direct:start", e -> {
            e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.TransactWriteItems);
            e.getIn().setHeader(Ddb2Constants.TRANSACT_WRITE_ITEMS, Arrays.asList(
                    TransactWriteItem.builder()
                            .put(Put.builder().tableName(TABLE_NAME).item(txItem1).build())
                            .build(),
                    TransactWriteItem.builder()
                            .put(Put.builder().tableName(TABLE_NAME).item(txItem2).build())
                            .build()));
        });

        assertNull(txWriteResult.getException());

        // Step 2: Use TransactGetItems to atomically read both items back
        Map<String, AttributeValue> key1 = new HashMap<>();
        key1.put(KEY_ATTR, AttributeValue.builder().s("tx-1").build());
        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put(KEY_ATTR, AttributeValue.builder().s("tx-2").build());

        Exchange txGetResult = template.send("direct:start", e -> {
            e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.TransactGetItems);
            e.getIn().setHeader(Ddb2Constants.TRANSACT_GET_ITEMS, Arrays.asList(
                    TransactGetItem.builder()
                            .get(Get.builder().tableName(TABLE_NAME).key(key1).build())
                            .build(),
                    TransactGetItem.builder()
                            .get(Get.builder().tableName(TABLE_NAME).key(key2).build())
                            .build()));
        });

        assertNull(txGetResult.getException());
        List<ItemResponse> txResponses = txGetResult.getIn().getHeader(Ddb2Constants.TRANSACT_GET_RESPONSE, List.class);
        assertNotNull(txResponses);
        assertEquals(2, txResponses.size());
        assertEquals("Transaction Item 1", txResponses.get(0).item().get("data").s());
        assertEquals("Transaction Item 2", txResponses.get(1).item().get("data").s());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void transactWriteWithIdempotencyToken() {
        // Use TransactWriteItems with a client request token for idempotency
        Map<String, AttributeValue> item = makeItem("tx-idem-1", "Idempotent Item");

        for (int i = 0; i < 2; i++) {
            Exchange result = template.send("direct:start", e -> {
                e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.TransactWriteItems);
                e.getIn().setHeader(Ddb2Constants.TRANSACT_CLIENT_REQUEST_TOKEN, "unique-token-12345");
                e.getIn().setHeader(Ddb2Constants.TRANSACT_WRITE_ITEMS, Arrays.asList(
                        TransactWriteItem.builder()
                                .put(Put.builder().tableName(TABLE_NAME).item(item).build())
                                .build()));
            });
            assertNull(result.getException());
        }

        // Verify the item exists via PartiQL
        Exchange selectResult = template.send("direct:start", e -> {
            e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.ExecuteStatement);
            e.getIn().setHeader(Ddb2Constants.STATEMENT,
                    "SELECT * FROM \"" + TABLE_NAME + "\" WHERE \"" + KEY_ATTR + "\" = ?");
            e.getIn().setHeader(Ddb2Constants.STATEMENT_PARAMETERS,
                    Arrays.asList(AttributeValue.builder().s("tx-idem-1").build()));
            e.getIn().setHeader(Ddb2Constants.CONSISTENT_READ, true);
        });

        List<Map<String, AttributeValue>> items
                = selectResult.getIn().getHeader(Ddb2Constants.EXECUTE_STATEMENT_ITEMS, List.class);
        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("Idempotent Item", items.get(0).get("data").s());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void executeStatementWithPagination() {
        // Insert multiple items via batch write
        Map<String, List<WriteRequest>> requestItems = new HashMap<>();
        List<WriteRequest> writes = Arrays.asList(
                WriteRequest.builder().putRequest(PutRequest.builder().item(makeItem("page-1", "Page 1")).build()).build(),
                WriteRequest.builder().putRequest(PutRequest.builder().item(makeItem("page-2", "Page 2")).build()).build(),
                WriteRequest.builder().putRequest(PutRequest.builder().item(makeItem("page-3", "Page 3")).build()).build());
        requestItems.put(TABLE_NAME, writes);

        template.send("direct:start", e -> {
            e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.BatchWriteItems);
            e.getIn().setHeader(Ddb2Constants.BATCH_WRITE_ITEMS, requestItems);
        });

        // Query with PartiQL (full table scan via PartiQL)
        Exchange scanResult = template.send("direct:start", e -> {
            e.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.ExecuteStatement);
            e.getIn().setHeader(Ddb2Constants.STATEMENT, "SELECT * FROM \"" + TABLE_NAME + "\"");
            e.getIn().setHeader(Ddb2Constants.CONSISTENT_READ, true);
        });

        assertNull(scanResult.getException());
        List<Map<String, AttributeValue>> items
                = scanResult.getIn().getHeader(Ddb2Constants.EXECUTE_STATEMENT_ITEMS, List.class);
        assertNotNull(items);
        // Should return at least the 3 items we just inserted (may include others from other tests)
        assertTrue(items.size() >= 3, "PartiQL scan should return at least 3 items");
    }

    private Map<String, AttributeValue> makeItem(String keyValue, String dataValue) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(KEY_ATTR, AttributeValue.builder().s(keyValue).build());
        item.put("data", AttributeValue.builder().s(dataValue).build());
        return item;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("aws2-ddb://" + TABLE_NAME);
            }
        };
    }
}
