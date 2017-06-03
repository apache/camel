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
package org.apache.camel.component.mongodb;

public final class MongoDbConstants {

    public static final String OPERATION_HEADER = "CamelMongoDbOperation";
    public static final String RESULT_TOTAL_SIZE = "CamelMongoDbResultTotalSize";
    public static final String RESULT_PAGE_SIZE = "CamelMongoDbResultPageSize";
    public static final String FIELDS_FILTER = "CamelMongoDbFieldsFilter";
    public static final String BATCH_SIZE = "CamelMongoDbBatchSize";
    public static final String NUM_TO_SKIP = "CamelMongoDbNumToSkip";
    public static final String INSERT_RECORDS_AFFECTED = "CamelMongoDbInsertRecordsAffected";
    public static final String MULTIUPDATE = "CamelMongoDbMultiUpdate";
    public static final String MULTIINSERT = "CamelMongoDbMultiInsert";
    public static final String UPSERT = "CamelMongoDbUpsert";
    public static final String RECORDS_AFFECTED = "CamelMongoDbRecordsAffected";
    public static final String SORT_BY = "CamelMongoDbSortBy";
    public static final String DATABASE = "CamelMongoDbDatabase";
    public static final String COLLECTION = "CamelMongoDbCollection";
    public static final String COLLECTION_INDEX = "CamelMongoDbCollectionIndex";
    public static final String WRITECONCERN = "CamelMongoDbWriteConcern";
    public static final String LIMIT = "CamelMongoDbLimit";
    public static final String FROM_TAILABLE = "CamelMongoDbTailable";
    public static final String WRITERESULT = "CamelMongoWriteResult";
    public static final String OID = "CamelMongoOid";
    public static final String DISTINCT_QUERY_FIELD = "CamelMongoDbDistinctQueryField";

    private MongoDbConstants() {
    }

}
