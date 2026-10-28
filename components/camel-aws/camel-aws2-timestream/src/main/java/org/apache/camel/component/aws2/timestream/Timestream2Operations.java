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
package org.apache.camel.component.aws2.timestream;

public enum Timestream2Operations {

    /** Describes Endpoints available for Write or Query **/
    describeEndpoints(null),

    /** Write Operations **/
    createBatchLoadTask(Timestream2ClientType.write),
    describeBatchLoadTask(Timestream2ClientType.write),
    resumeBatchLoadTask(Timestream2ClientType.write),
    listBatchLoadTasks(Timestream2ClientType.write),
    createDatabase(Timestream2ClientType.write),
    deleteDatabase(Timestream2ClientType.write),
    describeDatabase(Timestream2ClientType.write),
    updateDatabase(Timestream2ClientType.write),
    listDatabases(Timestream2ClientType.write),
    createTable(Timestream2ClientType.write),
    deleteTable(Timestream2ClientType.write),
    describeTable(Timestream2ClientType.write),
    updateTable(Timestream2ClientType.write),
    listTables(Timestream2ClientType.write),
    writeRecords(Timestream2ClientType.write),

    /** Query Operations **/
    createScheduledQuery(Timestream2ClientType.query),
    deleteScheduledQuery(Timestream2ClientType.query),
    executeScheduledQuery(Timestream2ClientType.query),
    updateScheduledQuery(Timestream2ClientType.query),
    describeScheduledQuery(Timestream2ClientType.query),
    listScheduledQueries(Timestream2ClientType.query),
    prepareQuery(Timestream2ClientType.query),
    query(Timestream2ClientType.query),
    cancelQuery(Timestream2ClientType.query);

    private final Timestream2ClientType clientType;

    Timestream2Operations(Timestream2ClientType clientType) {
        this.clientType = clientType;
    }

    /**
     * Checks that this is an operation of the given client type (describeEndpoints is an operation of both)
     *
     * @throws IllegalArgumentException if this is an operation of the other client type
     */
    public void checkClientType(Timestream2ClientType clientType) {
        if (this.clientType != null && this.clientType != clientType) {
            throw new IllegalArgumentException(
                    "The operation " + name() + " is not an operation of the " + clientType + " client: use clientType="
                                               + this.clientType);
        }
    }
}
