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

    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsRedshiftDataOperation";

    @Metadata(description = "The cluster identifier.", javaType = "String")
    String CLUSTER_IDENTIFIER = "CamelAwsRedshiftDataClusterIdentifier";

    @Metadata(description = "The name or ARN of the secret that enables access to the database.", javaType = "String")
    String SECRET_ARN = "CamelAwsRedshiftDataSecretArn";

    @Metadata(description = "The name of the database.", javaType = "String")
    String DATABASE = "CamelAwsRedshiftDataDatabase";

    @Metadata(description = "The serverless workgroup name or Amazon Resource Name (ARN).", javaType = "String")
    String WORKGROUP_NAME = "CamelAwsRedshiftDataWorkGroupName";

    @Metadata(description = "The maximum number of databases to return in the response.", javaType = "Integer")
    String LIST_DATABASES_MAX_RESULTS = "CamelAwsRedshiftDataDatabasesMaxResults";

    @Metadata(description = "The database user name.", javaType = "String")
    String DB_USER = "CamelAwsRedshiftDataDbUser";

    @Metadata(description = "A database name.", javaType = "String")
    String CONNECTED_DATABASE = "CamelAwsRedshiftDataConnectedDatabase";

    @Metadata(description = "A pattern to filter results by schema name.", javaType = "String")
    String SCHEMA_PATTERN = "CamelAwsRedshiftDataSchemaPattern";

    @Metadata(description = "The maximum number of schemas to return in the response.", javaType = "Integer")
    String LIST_SCHEMAS_MAX_RESULTS = "CamelAwsRedshiftDataSchemasMaxResults";

    @Metadata(description = "The maximum number of SQL statements to return in the response.", javaType = "Integer")
    String LIST_STATEMENTS_MAX_RESULTS = "CamelAwsRedshiftDataStatementsMaxResults";

    @Metadata(description = "The name of the SQL statement specified as input to BatchExecuteStatement or ExecuteStatement to identify the query.",
              javaType = "String")
    String STATEMENT_NAME = "CamelAwsRedshiftDataStatementName";

    @Metadata(description = "The status of the SQL statement to list.", javaType = "String")
    String STATUS = "CamelAwsRedshiftDataStatus";

    @Metadata(description = "A value that filters which statements to return in the response.", javaType = "Boolean")
    String ROLE_LEVEL = "CamelAwsRedshiftDataRoleLevel";

    @Metadata(description = "The maximum number of tables to return in the response.", javaType = "Integer")
    String LIST_TABLES_MAX_RESULTS = "CamelAwsRedshiftDataTablesMaxResults";

    @Metadata(description = "A pattern to filter results by table name.", javaType = "String")
    String TABLE_PATTERN = "CamelAwsRedshiftDataTablePattern";

    @Metadata(description = "The name of the table.", javaType = "String")
    String TABLE = "CamelAwsRedshiftDataTable";

    @Metadata(description = "The schema that contains the table.", javaType = "String")
    String SCHEMA = "CamelAwsRedshiftDataSchema";

    @Metadata(description = "The maximum number of tables to return in the response.", javaType = "Integer")
    String DESCRIBE_TABLE_MAX_RESULTS = "CamelAwsRedshiftDataDescribeTableMaxResults";

    @Metadata(description = "ID of the statement", javaType = "String")
    String STATEMENT_ID = "CamelAwsRedshiftDataStatementId";

    @Metadata(description = "A value that indicates whether to send an event to the Amazon EventBridge event bus after the SQL statement runs.",
              javaType = "Boolean")
    String WITH_EVENT = "CamelAwsRedshiftDataWithEvent";

    @Metadata(description = "A unique, case-sensitive identifier that you provide to ensure the idempotency of the request.",
              javaType = "String")
    String CLIENT_TOKEN = "CamelAwsRedshiftDataClientToken";

    @Metadata(description = "The SQL statement text to run.", javaType = "String")
    String SQL_STATEMENT = "CamelAwsRedshiftDataSqlStatement";

    @Metadata(description = "The parameters for the SQL statement.", javaType = "List<SqlParameterList>")
    String SQL_PARAMETER_LIST = "CamelAwsRedshiftDataSqlParameterList";

    @Metadata(description = "The List of SQL statements text to run.", javaType = "List")
    String SQL_STATEMENT_LIST = "CamelAwsRedshiftDataSqlStatementList";

}
