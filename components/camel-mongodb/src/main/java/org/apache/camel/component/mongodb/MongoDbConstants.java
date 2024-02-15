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
package org.apache.camel.component.mongodb;

import org.apache.camel.spi.Metadata;

public final class MongoDbConstants {

    @Metadata(label = "producer", description = "The operation this endpoint will execute against MongoDB.",
              javaType = "org.apache.camel.component.mongodb.MongoDbOperation or String")
    public static final String OPERATION_HEADER = "CamelMongoDbOperation";
    @Metadata(label = "producer findAll",
              description = "Number of objects matching the query. This does not take limit/skip into consideration.",
              javaType = "Integer")
    public static final String RESULT_TOTAL_SIZE = "CamelMongoDbResultTotalSize";
    @Metadata(label = "producer findAll",
              description = "Number of objects matching the query. This does not take limit/skip into consideration.",
              javaType = "Integer")
    public static final String RESULT_PAGE_SIZE = "CamelMongoDbResultPageSize";
    @Metadata(label = "producer", description = "The query to execute against MongoDB.", javaType = "org.bson.conversions.Bson")
    public static final String CRITERIA = "CamelMongoDbCriteria";
    @Metadata(label = "producer", description = "The project document.", javaType = "org.bson.conversions.Bson")
    public static final String FIELDS_PROJECTION = "CamelMongoDbFieldsProjection";
    @Metadata(label = "producer findAll aggregate", description = "The number of documents per batch.", javaType = "Integer")
    public static final String BATCH_SIZE = "CamelMongoDbBatchSize";
    @Metadata(label = "producer findAll", description = "Discards a given number of elements at the beginning of the cursor.",
              javaType = "Integer")
    public static final String NUM_TO_SKIP = "CamelMongoDbNumToSkip";
    public static final String INSERT_RECORDS_AFFECTED = "CamelMongoDbInsertRecordsAffected";
    @Metadata(label = "producer update", description = "If the update should be applied to all objects matching. See\n" +
                                                       "http://www.mongodb.org/display/DOCS/Atomic+Operations[Atomic Operations]",
              javaType = "Boolean")
    public static final String MULTIUPDATE = "CamelMongoDbMultiUpdate";
    @Metadata(label = "producer update", description = "If the database should create the element if it does not exist",
              javaType = "Boolean")
    public static final String UPSERT = "CamelMongoDbUpsert";
    @Metadata(label = "producer", description = "The number of modified or deleted records", javaType = "long")
    public static final String RECORDS_AFFECTED = "CamelMongoDbRecordsAffected";
    @Metadata(label = "producer", description = "The number of documents matched by the query.", javaType = "long")
    public static final String RECORDS_MATCHED = "CamelMongoDbRecordsMatched";
    @Metadata(label = "producer", description = "The sort criteria.", javaType = "Bson or Document")
    public static final String SORT_BY = "CamelMongoDbSortBy";
    @Metadata(description = "The name of the MongoDB database to target", javaType = "String")
    public static final String DATABASE = "CamelMongoDbDatabase";
    @Metadata(description = "The name of the MongoDB collection to bind to this endpoint", javaType = "String")
    public static final String COLLECTION = "CamelMongoDbCollection";
    @Metadata(label = "producer", description = "The list of dynamic indexes to create on the fly", javaType = "List<Bson>")
    public static final String COLLECTION_INDEX = "CamelMongoDbCollectionIndex";
    @Metadata(label = "producer findAll", description = "Limits the number of elements returned.", javaType = "Integer")
    public static final String LIMIT = "CamelMongoDbLimit";
    @Metadata(label = "consumer", description = "Is from tailable", javaType = "Boolean")
    public static final String FROM_TAILABLE = "CamelMongoDbTailable";
    @Metadata(label = "producer", description = "The result of the write operation", javaType = "Object")
    public static final String WRITERESULT = "CamelMongoWriteResult";
    @Metadata(label = "producer", description = "The OID(s) of the inserted record(s)", javaType = "Object or List<Object>")
    public static final String OID = "CamelMongoOid";
    @Metadata(label = "producer", description = "The specified field name fow which we want to get the distinct values.",
              javaType = "String")
    public static final String DISTINCT_QUERY_FIELD = "CamelMongoDbDistinctQueryField";
    @Metadata(label = "producer findAll aggregate", description = "Sets allowDiskUse MongoDB flag.\n" +
                                                                  "This is supported since MongoDB Server 4.3.1. Using this header with older MongoDB Server version can cause query to fail.",
              javaType = "Boolean")
    public static final String ALLOW_DISK_USE = "CamelMongoDbAllowDiskUse";
    @Metadata(label = "producer bulkWrite", description = "Perform an ordered or unordered operation execution. ",
              javaType = "Boolean", defaultValue = "TRUE")
    public static final String BULK_ORDERED = "CamelMongoDbBulkOrdered";
    @Metadata(label = "consumer changeStreams",
              description = "A document that contains the _id of the document created or modified by the insert,\n" +
                            "replace, delete, update operations (i.e. CRUD operations). For sharded collections, also displays the full shard key for\n"
                            +
                            "the document. The _id field is not repeated if it is already a part of the shard key.",
              javaType = "org.bson.types.ObjectId")
    public static final String MONGO_ID = "_id"; // default id field
    @Metadata(label = "consumer changeStreams", description = "The type of operation that occurred. Can\n" +
                                                              "be any of the following values: insert, delete, replace, update, drop, rename, dropDatabase, invalidate.",
              javaType = "String")
    public static final String STREAM_OPERATION_TYPE = "CamelMongoDbStreamOperationType";

    @Metadata(label = "producer update one and return", description = "Indicates which document to return,\n" +
                                                                      "the document before or after an update and return atomic operation.",
              javaType = "com.mongodb.client.model.ReturnDocument")
    public static final String RETURN_DOCUMENT = "CamelMongoDbReturnDocumentType";

    @Metadata(label = "producer update one and options", description = "Options to use.\n" +
                                                                       "When set, options set in the headers will be ignored.",
              javaType = "Object")
    public static final String OPTIONS = "CamelMongoDbOperationOption";

    private MongoDbConstants() {
    }

}
