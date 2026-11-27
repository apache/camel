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
package org.apache.camel.component.aws2.redshift.data;

import org.apache.camel.spi.Metadata;

public interface RedshiftData2Constants {

    @Metadata(description = "The operation to perform. It replaces the operation option.",
              javaType = "String")
    String OPERATION = "CamelAwsRedshiftDataOperation";

    @Metadata(description = "The identifier of the provisioned cluster to connect to. Amazon Redshift requires it for a cluster"
                            + " with AWS Secrets Manager or temporary credentials. Not used by listStatements, cancelStatement,"
                            + " describeStatement and getStatementResult.",
              javaType = "String")
    String CLUSTER_IDENTIFIER = "CamelAwsRedshiftDataClusterIdentifier";

    @Metadata(description = "The name or ARN of the secret in AWS Secrets Manager that holds the user name and password of the"
                            + " database. Amazon Redshift requires it when it authenticates with AWS Secrets Manager. Not used by"
                            + " listStatements, cancelStatement, describeStatement and getStatementResult.",
              javaType = "String")
    String SECRET_ARN = "CamelAwsRedshiftDataSecretArn";

    @Metadata(description = "The name of the database. For listSchemas, listTables and describeTable it is the database that"
                            + " holds the schemas or tables, and also the database to connect to when"
                            + " CamelAwsRedshiftDataConnectedDatabase is not set. Not used by listStatements, cancelStatement,"
                            + " describeStatement and getStatementResult.",
              javaType = "String")
    String DATABASE = "CamelAwsRedshiftDataDatabase";

    @Metadata(description = "The name or ARN of the Amazon Redshift Serverless workgroup to connect to. Amazon Redshift requires"
                            + " it for a workgroup with AWS Secrets Manager or temporary credentials. Not used by listStatements,"
                            + " cancelStatement, describeStatement and getStatementResult.",
              javaType = "String")
    String WORKGROUP_NAME = "CamelAwsRedshiftDataWorkGroupName";

    @Metadata(description = "The maximum number of databases that listDatabases returns. The producer returns only the first page"
                            + " of the response.",
              javaType = "Integer")
    String LIST_DATABASES_MAX_RESULTS = "CamelAwsRedshiftDataDatabasesMaxResults";

    @Metadata(description = "The database user name. Amazon Redshift requires it to connect to a cluster as a database user with"
                            + " temporary credentials. Not used by listStatements, cancelStatement, describeStatement and"
                            + " getStatementResult.",
              javaType = "String")
    String DB_USER = "CamelAwsRedshiftDataDbUser";

    @Metadata(description = "The database to connect to with the credentials, when it is not the database in"
                            + " CamelAwsRedshiftDataDatabase. Used by listSchemas, listTables and describeTable.",
              javaType = "String")
    String CONNECTED_DATABASE = "CamelAwsRedshiftDataConnectedDatabase";

    @Metadata(description = "A pattern of schema names for listSchemas and listTables, in which % matches any number of"
                            + " characters and _ matches one character. If not set, all schemas match.",
              javaType = "String")
    String SCHEMA_PATTERN = "CamelAwsRedshiftDataSchemaPattern";

    @Metadata(description = "The maximum number of schemas that listSchemas returns. The producer returns only the first page of"
                            + " the response.",
              javaType = "Integer")
    String LIST_SCHEMAS_MAX_RESULTS = "CamelAwsRedshiftDataSchemasMaxResults";

    @Metadata(description = "The maximum number of SQL statements that listStatements returns, at most 100. The producer returns"
                            + " only the first page of the response.",
              javaType = "Integer")
    String LIST_STATEMENTS_MAX_RESULTS = "CamelAwsRedshiftDataStatementsMaxResults";

    @Metadata(description = "The name of the SQL statement. executeStatement and batchExecuteStatement give the statement this"
                            + " name, and listStatements returns the statements whose name starts with this value, which is"
                            + " case-sensitive.",
              javaType = "String")
    String STATEMENT_NAME = "CamelAwsRedshiftDataStatementName";

    @Metadata(description = "The status of the statements that listStatements returns: SUBMITTED, PICKED, STARTED, FINISHED,"
                            + " ABORTED, FAILED or ALL. If not set, Amazon Redshift returns only the finished statements.",
              javaType = "String")
    String STATUS = "CamelAwsRedshiftDataStatus";

    @Metadata(description = "Whether listStatements returns all statements that the IAM role of the caller ran (true), or only"
                            + " those of the current IAM session (false). If not set, Amazon Redshift uses true.",
              javaType = "Boolean")
    String ROLE_LEVEL = "CamelAwsRedshiftDataRoleLevel";

    @Metadata(description = "The maximum number of tables that listTables returns, at most 1000. The producer returns only the"
                            + " first page of the response.",
              javaType = "Integer")
    String LIST_TABLES_MAX_RESULTS = "CamelAwsRedshiftDataTablesMaxResults";

    @Metadata(description = "A pattern of table names for listTables, in which % matches any number of characters and _ matches"
                            + " one character. If not set, all tables match.",
              javaType = "String")
    String TABLE_PATTERN = "CamelAwsRedshiftDataTablePattern";

    @Metadata(description = "The name of the table that describeTable describes. If not set, describeTable describes all tables"
                            + " of the matching schemas.",
              javaType = "String")
    String TABLE = "CamelAwsRedshiftDataTable";

    @Metadata(description = "The schema of the table that describeTable describes. If not set, the tables of all schemas match.",
              javaType = "String")
    String SCHEMA = "CamelAwsRedshiftDataSchema";

    @Metadata(description = "The maximum number of columns that describeTable returns, at most 1000. The producer returns only"
                            + " the first page of the response.",
              javaType = "Integer")
    String DESCRIBE_TABLE_MAX_RESULTS = "CamelAwsRedshiftDataDescribeTableMaxResults";

    @Metadata(description = "The ID of the SQL statement for cancelStatement, describeStatement and getStatementResult, as the"
                            + " response of executeStatement or batchExecuteStatement returns it. For the result of one statement of"
                            + " a batch, add its number as a suffix, for example :2 for the second.",
              javaType = "String")
    String STATEMENT_ID = "CamelAwsRedshiftDataStatementId";

    @Metadata(description = "Whether Amazon Redshift sends an event to the Amazon EventBridge event bus after the SQL statements"
                            + " run. Used by executeStatement and batchExecuteStatement.",
              javaType = "Boolean")
    String WITH_EVENT = "CamelAwsRedshiftDataWithEvent";

    @Metadata(description = "A unique, case-sensitive identifier of at most 64 characters, which makes a retried request"
                            + " idempotent. Used by executeStatement and batchExecuteStatement.",
              javaType = "String")
    String CLIENT_TOKEN = "CamelAwsRedshiftDataClientToken";

    @Metadata(description = "The SQL statement that executeStatement runs, which must be a single statement. The producer does"
                            + " not wait for the statement to finish: describeStatement and getStatementResult take the ID that the"
                            + " response returns.",
              javaType = "String")
    String SQL_STATEMENT = "CamelAwsRedshiftDataSqlStatement";

    @Metadata(description = "The parameters of the SQL statement of executeStatement, as a java.util.List of"
                            + " software.amazon.awssdk.services.redshiftdata.model.SqlParameter, each with a name and a value. The"
                            + " statement refers to a parameter as :name.",
              javaType = "List<SqlParameterList>")
    String SQL_PARAMETER_LIST = "CamelAwsRedshiftDataSqlParameterList";

    @Metadata(description = "The SQL statements that batchExecuteStatement runs, as a java.util.List of String with at most 40"
                            + " statements. They run one after the other in a single transaction, which is rolled back if one of"
                            + " them fails.",
              javaType = "List")
    String SQL_STATEMENT_LIST = "CamelAwsRedshiftDataSqlStatementList";

}
