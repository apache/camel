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
package org.apache.camel.component.google.bigquery.integration;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableDefinition;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.bigquery.TableResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.bigquery.GoogleBigQueryComponent;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLComponent;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BigQueryTestSupport extends CamelTestSupport {
    public static final String SERVICE_KEY;
    public static final String SERVICE_ACCOUNT;
    public static final String PROJECT_ID;
    public static final String DATASET_ID;
    public static final String CREDENTIALS_FILE_LOCATION;

    private static final Logger LOGGER = LoggerFactory.getLogger(BigQueryTestSupport.class);

    private GoogleBigQueryConnectionFactory connectionFactory;

    static {
        Properties testProperties = loadProperties();
        SERVICE_KEY = testProperties.getProperty("service.key");
        SERVICE_ACCOUNT = testProperties.getProperty("service.account");
        PROJECT_ID = testProperties.getProperty("project.id");
        DATASET_ID = testProperties.getProperty("bigquery.datasetId");
        CREDENTIALS_FILE_LOCATION = testProperties.getProperty("service.credentialsFileLocation");
    }

    private static Properties loadProperties() {
        Properties testProperties = new Properties();
        InputStream fileIn = BigQueryTestSupport.class.getClassLoader().getResourceAsStream("simple.properties");
        try {
            testProperties.load(fileIn);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return testProperties;
    }

    protected void addBigqueryComponent(CamelContext context) {

        connectionFactory = new GoogleBigQueryConnectionFactory()
                .setCredentialsFileLocation(CREDENTIALS_FILE_LOCATION);

        GoogleBigQueryComponent component = new GoogleBigQueryComponent();
        component.setConnectionFactory(connectionFactory);

        context.addComponent("google-bigquery", component);
        context.getPropertiesComponent().setLocation("ref:prop");
    }

    protected void addBigquerySqlComponent(CamelContext context) {

        connectionFactory = new GoogleBigQueryConnectionFactory()
                .setCredentialsFileLocation(CREDENTIALS_FILE_LOCATION);

        GoogleBigQuerySQLComponent component = new GoogleBigQuerySQLComponent();
        component.setConnectionFactory(connectionFactory);

        context.addComponent("google-bigquery-sql", component);
        context.getPropertiesComponent().setLocation("ref:prop");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        addBigqueryComponent(context);
        addBigquerySqlComponent(context);
        return context;
    }

    @BindToRegistry("prop")
    public Properties loadRegProperties() throws Exception {
        return loadProperties();
    }

    public GoogleBigQueryConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    protected void assertRowExist(String tableName, Map<String, String> row) throws Exception {
        String query = "SELECT * FROM " + DATASET_ID + "." + tableName + " WHERE "
                       + row.entrySet().stream()
                               .map(e -> e.getKey() + " = '" + e.getValue() + "'")
                               .collect(Collectors.joining(" AND "));
        LOGGER.debug("Query: {}", query);
        QueryJobConfiguration queryJobConfiguration = QueryJobConfiguration.of(query);
        TableResult tableResult = getConnectionFactory()
                .getDefaultClient()
                .query(queryJobConfiguration, JobId.of(PROJECT_ID, UUID.randomUUID().toString()));
        assertEquals(1, tableResult.getTotalRows());
    }

    protected void createBqTable(String tableId) throws Exception {
        Schema schema = createSchema();
        TableId id = TableId.of(PROJECT_ID, DATASET_ID, tableId);
        TableDefinition.Builder builder = StandardTableDefinition.newBuilder().setSchema(schema);
        TableInfo tableInfo = TableInfo.of(id, builder.build());
        try {
            getConnectionFactory().getDefaultClient().create(tableInfo);
        } catch (GoogleJsonResponseException e) {
            if (e.getDetails().getCode() == 409) {
                LOGGER.info("Table {} already exist");
            } else {
                throw e;
            }
        }
    }

    private Schema createSchema() throws Exception {
        FieldList fields = FieldList.of(
                Field.of("id", StandardSQLTypeName.NUMERIC),
                Field.of("col1", StandardSQLTypeName.STRING),
                Field.of("col2", StandardSQLTypeName.STRING));
        return Schema.of(fields);
    }
}
