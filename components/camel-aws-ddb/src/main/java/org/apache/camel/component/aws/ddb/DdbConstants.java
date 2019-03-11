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

/**
 * Constants used in Camel AWS DynamoDB component
 */
public interface DdbConstants {
    String ATTRIBUTES = "CamelAwsDdbAttributes";
    String ATTRIBUTE_NAMES = "CamelAwsDdbAttributeNames";
    String BATCH_ITEMS = "CamelAwsDdbBatchItems";
    String BATCH_RESPONSE = "CamelAwsDdbBatchResponse";
    String CONSISTENT_READ = "CamelAwsDdbConsistentRead";
    String CONSUMED_CAPACITY = "CamelAwsDdbConsumedCapacity";
    String COUNT = "CamelAwsDdbCount";
    String CREATION_DATE = "CamelAwsDdbCreationDate";
    // Removed from DynamoDB v1 to v2
    // String EXACT_COUNT = "CamelAwsDdbExactCount";
    // Removed from DynamoDB v1 to v2
    // String HASH_KEY_VALUE = "CamelAwsDdbHashKeyValue";
    // Added INDEX_NAME for querying secondary indexes
    String INDEX_NAME = "CamelAwsDdbIndexName";
    String ITEM = "CamelAwsDdbItem";
    String ITEMS = "CamelAwsDdbItems";
    String ITEM_COUNT = "CamelAwsDdbTableItemCount";
    String ITEM_NAME = "CamelAwsDdbItemName";
    String MESSAGE_ID = "CamelAwsDdbMessageId";
    String NEXT_TOKEN = "CamelAwsDdbNextToken";
    String KEY = "CamelAwsDdbKey";
    // Added from DynamoDB v1 to v2
    String KEY_CONDITIONS = "CamelAwsDdbKeyConditions";
    String KEY_SCHEMA = "CamelAwsDdbKeySchema";
    String LAST_EVALUATED_KEY = "CamelAwsDdbLastEvaluatedKey";
    String LIMIT = "CamelAwsDdbLimit";
    String OPERATION = "CamelAwsDdbOperation";
    String PROVISIONED_THROUGHPUT = "CamelAwsDdbProvisionedThroughput";
    String READ_CAPACITY = "CamelAwsDdbReadCapacity";
    String RETURN_VALUES = "CamelAwsDdbReturnValues";
    String SCANNED_COUNT = "CamelAwsDdbScannedCount";
    String SCAN_INDEX_FORWARD = "CamelAwsDdbScanIndexForward";
    // Removed from DynamoDB v1 to v2
    // String SCAN_RANGE_KEY_CONDITION = "CamelAwsDdbScanRangeKeyCondition";
    String SCAN_FILTER = "CamelAwsDdbScanFilter";
    String START_KEY = "CamelAwsDdbStartKey";
    String TABLE_NAME = "CamelAwsDdbTableName";
    String TABLE_SIZE = "CamelAwsDdbTableSize";
    String TABLE_STATUS = "CamelAwsDdbTableStatus";
    String UPDATE_CONDITION = "CamelAwsDdbUpdateCondition";
    String UPDATE_VALUES = "CamelAwsDdbUpdateValues";
    String UNPROCESSED_KEYS = "CamelAwsDdbUnprocessedKeys";
    String WRITE_CAPACITY = "CamelAwsDdbWriteCapacity";
}
