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

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS DynamoDB component
 */
public interface Ddb2Constants {
    @Metadata(label = "DeleteItem GetItem PutItem UpdateItem",
              description = "The list of attributes returned by the operation.", javaType = "Map<String, AttributeValue>")
    String ATTRIBUTES = "CamelAwsDdbAttributes";
    @Metadata(description = """
            If attribute names are not specified then all attributes will be
            returned.""",
              javaType = "Collection<String>")
    String ATTRIBUTE_NAMES = "CamelAwsDdbAttributeNames";
    @Metadata(description = "A map of the table name and corresponding items to get by primary key.",
              javaType = "Map<String, KeysAndAttributes>")
    String BATCH_ITEMS = "CamelAwsDdbBatchItems";
    @Metadata(label = "BatchGetItems", description = "Table names and the respective item attributes from the tables.",
              javaType = "Map<String, BatchResponse>")
    String BATCH_RESPONSE = "CamelAwsDdbBatchResponse";
    @Metadata(description = """
            If set to true, then a consistent read is issued, otherwise eventually
            consistent is used.""",
              javaType = "Boolean")
    String CONSISTENT_READ = "CamelAwsDdbConsistentRead";
    @Metadata(label = "Query Scan", description = "The number of Capacity Units of the provisioned throughput of the table\n" +
                                                  "consumed during the operation.",
              javaType = "Double")
    String CONSUMED_CAPACITY = "CamelAwsDdbConsumedCapacity";
    @Metadata(label = "Query Scan", description = "Number of items in the response.", javaType = "Integer")
    String COUNT = "CamelAwsDdbCount";
    @Metadata(label = "DeleteTable DescribeTable", description = "Creation DateTime of this table.", javaType = "Date")
    String CREATION_DATE = "CamelAwsDdbCreationDate";
    // Removed from DynamoDB v1 to v2
    // @Metadata(description = "If set to true, Amazon DynamoDB returns a total number of items that\n" +
    //     "match the query parameters, instead of a list of the matching items and\n" +
    //     "their attributes.", javaType = "Boolean")
    // String EXACT_COUNT = "CamelAwsDdbExactCount";
    // Removed from DynamoDB v1 to v2
    // @Metadata(description = "Value of the hash component of the composite primary key.", javaType = "AttributeValue")
    // String HASH_KEY_VALUE = "CamelAwsDdbHashKeyValue";
    // Added INDEX_NAME for querying secondary indexes
    @Metadata(description = "If set will be used as Secondary Index for Query operation.", javaType = "String")
    String INDEX_NAME = "CamelAwsDdbIndexName";
    @Metadata(description = "A map of the attributes for the item, and must include the primary key\n" +
                            "values that define the item.",
              javaType = "Map<String, AttributeValue>")
    String ITEM = "CamelAwsDdbItem";
    @Metadata(label = "Query Scan", description = "The list of attributes returned by the operation.",
              javaType = "List<Map<String,AttributeValue>>")
    String ITEMS = "CamelAwsDdbItems";
    @Metadata(label = "DeleteTable DescribeTable", description = "Item count for this table.", javaType = "Long")
    String ITEM_COUNT = "CamelAwsDdbTableItemCount";
    String ITEM_NAME = "CamelAwsDdbItemName";
    String MESSAGE_ID = "CamelAwsDdbMessageId";
    String NEXT_TOKEN = "CamelAwsDdbNextToken";
    @Metadata(description = "The primary key that uniquely identifies each item in a table.",
              javaType = "Map<String, AttributeValue>")
    String KEY = "CamelAwsDdbKey";
    // Added from DynamoDB v1 to v2
    @Metadata(description = "This header specify the selection criteria for the\n" +
                            "query, and merge together the two old headers *CamelAwsDdbHashKeyValue*\n" +
                            "and *CamelAwsDdbScanRangeKeyCondition*",
              javaType = "Map<String, Condition>")
    String KEY_CONDITIONS = "CamelAwsDdbKeyConditions";
    @Metadata(label = "DeleteTable DescribeTable",
              description = "The KeySchema that identifies the primary key for this table.\n" +
                            "*From Camel 2.16.0 the type of this header is List<KeySchemaElement> and not KeySchema*",
              javaType = "List<KeySchemaElement>")
    String KEY_SCHEMA = "CamelAwsDdbKeySchema";
    @Metadata(label = "Query Scan", description = "Primary key of the item where the query operation stopped, inclusive of\n" +
                                                  "the previous result set.",
              javaType = "Key")
    String LAST_EVALUATED_KEY = "CamelAwsDdbLastEvaluatedKey";
    @Metadata(label = "Query Scan",
              description = "Whether the response has more results (is truncated). If true, use LAST_EVALUATED_KEY as START_KEY for the next page.",
              javaType = "Boolean")
    String IS_TRUNCATED = "CamelAwsDdbIsTruncated";
    @Metadata(description = "The maximum number of items to return.", javaType = "Integer")
    String LIMIT = "CamelAwsDdbLimit";
    @Metadata(description = "The operation to perform.", javaType = "org.apache.camel.component.aws2.ddb.Ddb2Operations")
    String OPERATION = "CamelAwsDdbOperation";
    @Metadata(label = "DeleteTable DescribeTable",
              description = "The value of the ProvisionedThroughput property for this table",
              javaType = "software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputDescription")
    String PROVISIONED_THROUGHPUT = "CamelAwsDdbProvisionedThroughput";
    @Metadata(label = "UpdateTable DescribeTable", description = "ReadCapacityUnits property of this table.", javaType = "Long")
    String READ_CAPACITY = "CamelAwsDdbReadCapacity";
    @Metadata(description = "Use this parameter if you want to get the attribute name-value pairs\n" +
                            "before or after they are modified(NONE, ALL_OLD, UPDATED_OLD, ALL_NEW,\n" +
                            "UPDATED_NEW).",
              javaType = "String")
    String RETURN_VALUES = "CamelAwsDdbReturnValues";
    @Metadata(label = "Scan", description = "Number of items in the complete scan before any filters are applied.",
              javaType = "Integer")
    String SCANNED_COUNT = "CamelAwsDdbScannedCount";
    @Metadata(description = "Specifies forward or backward traversal of the index.", javaType = "Boolean")
    String SCAN_INDEX_FORWARD = "CamelAwsDdbScanIndexForward";
    // Removed from DynamoDB v1 to v2
    // @Metadata(description = "A container for the attribute values and comparison operators to use for\n" +
    //    "the query.", javaType = "Condition")
    // String SCAN_RANGE_KEY_CONDITION = "CamelAwsDdbScanRangeKeyCondition";
    @Metadata(description = "Evaluates the scan results and returns only the desired values.",
              javaType = "Map<String, Condition>")
    String SCAN_FILTER = "CamelAwsDdbScanFilter";
    @Metadata(description = "Primary key of the item from which to continue an earlier query.",
              javaType = "Map<String, AttributeValue>")
    String START_KEY = "CamelAwsDdbStartKey";
    @Metadata(description = "Table Name for this operation.", javaType = "String")
    String TABLE_NAME = "CamelAwsDdbTableName";
    @Metadata(label = "DeleteTable DescribeTable", description = "The table size in bytes.", javaType = "Long")
    String TABLE_SIZE = "CamelAwsDdbTableSize";
    @Metadata(label = "DeleteTable DescribeTable",
              description = "The status of the table: CREATING, UPDATING, DELETING, ACTIVE", javaType = "String")
    String TABLE_STATUS = "CamelAwsDdbTableStatus";
    @Metadata(description = "Designates an attribute for a conditional modification.",
              javaType = "Map<String, ExpectedAttributeValue>")
    String UPDATE_CONDITION = "CamelAwsDdbUpdateCondition";
    @Metadata(description = "Map of attribute name to the new value and action for the update.",
              javaType = "Map<String, AttributeValueUpdate>")
    String UPDATE_VALUES = "CamelAwsDdbUpdateValues";
    @Metadata(label = "BatchGetItems", description = "Contains a map of tables and their respective keys that were not\n" +
                                                     "processed with the current response.",
              javaType = "Map<String,KeysAndAttributes>")
    String UNPROCESSED_KEYS = "CamelAwsDdbUnprocessedKeys";
    @Metadata(label = "UpdateTable DescribeTable", description = "WriteCapacityUnits property of this table.",
              javaType = "Long")
    String WRITE_CAPACITY = "CamelAwsDdbWriteCapacity";
    @Metadata(label = "ExecuteStatement",
              description = "A PartiQL statement that uses parameters.",
              javaType = "String")
    String STATEMENT = "CamelAwsDdbStatement";
    @Metadata(label = "ExecuteStatement",
              description = "The parameters for the PartiQL statement, if any.",
              javaType = "java.util.List<software.amazon.awssdk.services.dynamodb.model.AttributeValue>")
    String STATEMENT_PARAMETERS = "CamelAwsDdbStatementParameters";
    @Metadata(label = "BatchExecuteStatement",
              description = "The list of PartiQL statements representing the batch to run.",
              javaType = "java.util.List<software.amazon.awssdk.services.dynamodb.model.BatchStatementRequest>")
    String BATCH_STATEMENTS = "CamelAwsDdbBatchStatements";
    @Metadata(label = "ExecuteStatement",
              description = "The response items from an ExecuteStatement operation.",
              javaType = "java.util.List<java.util.Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>>")
    String EXECUTE_STATEMENT_ITEMS = "CamelAwsDdbExecuteStatementItems";
    @Metadata(label = "BatchExecuteStatement",
              description = "The response to each PartiQL statement in the batch.",
              javaType = "java.util.List<software.amazon.awssdk.services.dynamodb.model.BatchStatementResponse>")
    String BATCH_STATEMENT_RESPONSE = "CamelAwsDdbBatchStatementResponse";
    @Metadata(label = "TransactWriteItems",
              description = "The list of TransactWriteItem objects for a transactional write.",
              javaType = "java.util.List<software.amazon.awssdk.services.dynamodb.model.TransactWriteItem>")
    String TRANSACT_WRITE_ITEMS = "CamelAwsDdbTransactWriteItems";
    @Metadata(label = "TransactWriteItems",
              description = "A unique client request token for idempotent TransactWriteItems calls.",
              javaType = "String")
    String TRANSACT_CLIENT_REQUEST_TOKEN = "CamelAwsDdbTransactClientRequestToken";
    @Metadata(label = "TransactWriteItems",
              description = "The consumed capacity from a TransactWriteItems operation.",
              javaType = "java.util.List<software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity>")
    String TRANSACT_WRITE_CONSUMED_CAPACITY = "CamelAwsDdbTransactWriteConsumedCapacity";
    @Metadata(label = "TransactWriteItems",
              description = "The item collection metrics from a TransactWriteItems operation.",
              javaType = "java.util.Map<String, java.util.List<software.amazon.awssdk.services.dynamodb.model.ItemCollectionMetrics>>")
    String TRANSACT_WRITE_ITEM_COLLECTION_METRICS = "CamelAwsDdbTransactWriteItemCollectionMetrics";
    @Metadata(label = "TransactGetItems",
              description = "The list of TransactGetItem objects for a transactional read.",
              javaType = "java.util.List<software.amazon.awssdk.services.dynamodb.model.TransactGetItem>")
    String TRANSACT_GET_ITEMS = "CamelAwsDdbTransactGetItems";
    @Metadata(label = "TransactGetItems",
              description = "The response from a TransactGetItems operation.",
              javaType = "java.util.List<software.amazon.awssdk.services.dynamodb.model.ItemResponse>")
    String TRANSACT_GET_RESPONSE = "CamelAwsDdbTransactGetResponse";
    @Metadata(label = "BatchWriteItems",
              description = "A map of table names to lists of WriteRequest objects for batch writes.",
              javaType = "java.util.Map<String, java.util.List<software.amazon.awssdk.services.dynamodb.model.WriteRequest>>")
    String BATCH_WRITE_ITEMS = "CamelAwsDdbBatchWriteItems";
    @Metadata(label = "BatchWriteItems",
              description = "A map of tables and their respective unprocessed items after a BatchWriteItems operation.",
              javaType = "java.util.Map<String, java.util.List<software.amazon.awssdk.services.dynamodb.model.WriteRequest>>")
    String BATCH_WRITE_UNPROCESSED_ITEMS = "CamelAwsDdbBatchWriteUnprocessedItems";
    @Metadata(label = "Query Scan", description = "The Filter Expression.",
              javaType = "String")
    String FILTER_EXPRESSION = "CamelAwsDdbFilterExpression";
    @Metadata(label = "Query Scan", description = "The Filter Expression Attribute Names.",
              javaType = "Map<String, String>")
    String FILTER_EXPRESSION_ATTRIBUTE_NAMES = "CamelAwsDdbFilterExpressionAttributeNames";
    @Metadata(label = "Query Scan", description = "The Filter Expression Attribute Values.",
              javaType = "Map<String, String>")
    String FILTER_EXPRESSION_ATTRIBUTE_VALUES = "CamelAwsDdbFilterExpressionAttributeValues";
    @Metadata(label = "Query Scan", description = "The Project Expression.",
              javaType = "String")
    String PROJECT_EXPRESSION = "CamelAwsDdbProjectExpression";
}
