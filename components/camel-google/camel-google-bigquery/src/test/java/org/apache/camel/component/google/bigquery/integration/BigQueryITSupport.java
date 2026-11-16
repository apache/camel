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

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.cloud.NoCredentials;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.DatasetInfo;
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
import org.apache.camel.component.google.bigquery.GoogleBigQueryConfiguration;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLComponent;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLConfiguration;
import org.apache.camel.test.infra.google.bigquery.services.GoogleBigQueryService;
import org.apache.camel.test.infra.google.bigquery.services.GoogleBigQueryServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the integration tests against the BigQuery of Floci GCP, or with {@code -Dgoogle.instance.type=remote} against
 * the one of Google Cloud, with the credentials of {@code simple.properties}. Either way, the tests take the project
 * and the dataset of {@code simple.properties}: Floci GCP accepts any project id.
 */
public class BigQueryITSupport extends CamelTestSupport {
    @RegisterExtension
    public static GoogleBigQueryService service = GoogleBigQueryServiceFactory.createSingletonService();

    public static final String SERVICE_KEY;
    public static final String SERVICE_ACCOUNT;
    public static final String PROJECT_ID;
    public static final String DATASET_ID;
    public static final String CREDENTIALS_FILE_LOCATION;

    private static final Logger LOGGER = LoggerFactory.getLogger(BigQueryITSupport.class);

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
        return TestSupport.loadExternalPropertiesQuietly(BigQueryITSupport.class.getClassLoader(), "simple.properties");
    }

    protected void addBigqueryComponent(CamelContext context) {

        GoogleBigQueryConfiguration configuration = new GoogleBigQueryConfiguration()
                .setServiceAccountKey(CREDENTIALS_FILE_LOCATION);

        configuration.setConnectionFactory(connectionFactory);

        GoogleBigQueryComponent component = new GoogleBigQueryComponent(configuration);
        component.setConnectionFactory(connectionFactory);

        context.addComponent("google-bigquery", component);
        context.getPropertiesComponent().setLocation("ref:prop");
    }

    protected void addBigquerySqlComponent(CamelContext context) {

        GoogleBigQuerySQLConfiguration configuration = new GoogleBigQuerySQLConfiguration()
                .setServiceAccountKey(CREDENTIALS_FILE_LOCATION);
        configuration.setConnectionFactory(connectionFactory);

        GoogleBigQuerySQLComponent component = new GoogleBigQuerySQLComponent(configuration);
        component.setConnectionFactory(connectionFactory);

        context.addComponent("google-bigquery-sql", component);
        context.getPropertiesComponent().setLocation("ref:prop");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        connectionFactory = createConnectionFactory(context);
        addBigqueryComponent(context);
        addBigquerySqlComponent(context);
        if (!isRemote()) {
            createLocalDataset();
        }
        return context;
    }

    // the BigQuery of Google Cloud with -Dgoogle.instance.type=remote, and the one of Floci GCP otherwise
    private static boolean isRemote() {
        return "remote".equals(System.getProperty("google.instance.type"));
    }

    // the credentials that ran the tests against a real project, before they ran on Floci GCP
    private static boolean hasCredentials() {
        return SERVICE_KEY != null && SERVICE_ACCOUNT != null || CREDENTIALS_FILE_LOCATION != null;
    }

    private static GoogleBigQueryConnectionFactory createConnectionFactory(CamelContext context) {
        if (isRemote()) {
            return new GoogleBigQueryConnectionFactory()
                    .setServiceAccountKeyFile(CREDENTIALS_FILE_LOCATION)
                    .setCamelContext(context);
        }
        // so that a run on Floci GCP is not taken for one against the project of the credentials
        if (hasCredentials()) {
            throw new IllegalStateException(
                    "simple.properties gives credentials: add -Dgoogle.instance.type=remote to run the tests against"
                                            + " the BigQuery of that project");
        }
        // Floci GCP accepts any credentials: the client of the factory would take the application default ones
        return new GoogleBigQueryConnectionFactory(
                BigQueryOptions.newBuilder()
                        .setHost(service.endpoint())
                        .setProjectId(PROJECT_ID)
                        .setCredentials(NoCredentials.getInstance())
                        .build()
                        .getService());
    }

    // the project of Google Cloud has the dataset of the tests, Floci GCP starts without it
    private void createLocalDataset() throws Exception {
        try {
            getConnectionFactory().getDefaultClient().create(DatasetInfo.of(DatasetId.of(PROJECT_ID, DATASET_ID)));
        } catch (BigQueryException e) {
            if (e.getCode() != 409) {
                throw e;
            }
        }
    }

    @BindToRegistry("prop")
    public Properties loadRegProperties() throws IOException {
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
        } catch (BigQueryException e) {
            if (e.getCode() == 409) {
                LOGGER.info("TableId {} already exists", tableId);
            } else {
                throw e;
            }
        }
    }

    private Schema createSchema() {
        FieldList fields = FieldList.of(
                Field.of("id", StandardSQLTypeName.NUMERIC),
                Field.of("col1", StandardSQLTypeName.STRING),
                Field.of("col2", StandardSQLTypeName.STRING));
        return Schema.of(fields);
    }
}
