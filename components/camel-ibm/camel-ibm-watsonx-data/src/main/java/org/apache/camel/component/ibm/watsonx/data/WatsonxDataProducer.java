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
package org.apache.camel.component.ibm.watsonx.data;

import java.util.Map;

import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.WatsonxData;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.Catalog;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.CatalogCollection;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.ColumnsCollection;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.CreateSchemaOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.CreateStorageRegistrationOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.DeleteCatalogOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.DeleteSchemaOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.DeleteTableOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.GetAllColumnsOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.GetCatalogOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.GetPrestissimoEngineOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.GetPrestoEngineOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.GetTableOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.ListCatalogsOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.ListPrestissimoEnginesOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.ListPrestoEnginesOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.ListSchemasOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.ListStorageRegistrationsOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.ListTablesOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.PrestissimoEngine;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.PrestissimoEngineCollection;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.PrestoEngine;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.PrestoEngineCollection;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.RegisterTableCreatedBody;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.RegisterTableOptions;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.SchemaPrototype;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.SchemasCollection;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.StorageRegistration;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.StorageRegistrationCollection;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.Table;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.TableCollection;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.UpdateTableOptions;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for IBM watsonx.data component.
 */
public class WatsonxDataProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxDataProducer.class);

    public WatsonxDataProducer(WatsonxDataEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        WatsonxDataOperations operation = determineOperation(exchange);
        LOG.debug("Processing operation: {}", operation);

        WatsonxData service = getEndpoint().getWatsonxDataService();
        WatsonxDataConfiguration config = getEndpoint().getConfiguration();

        switch (operation) {
            case listCatalogs:
                handleListCatalogs(exchange, service, config);
                break;
            case getCatalog:
                handleGetCatalog(exchange, service, config);
                break;
            case deleteCatalog:
                handleDeleteCatalog(exchange, service, config);
                break;
            case listSchemas:
                handleListSchemas(exchange, service, config);
                break;
            case createSchema:
                handleCreateSchema(exchange, service, config);
                break;
            case deleteSchema:
                handleDeleteSchema(exchange, service, config);
                break;
            case listTables:
                handleListTables(exchange, service, config);
                break;
            case getTable:
                handleGetTable(exchange, service, config);
                break;
            case deleteTable:
                handleDeleteTable(exchange, service, config);
                break;
            case updateTable:
                handleUpdateTable(exchange, service, config);
                break;
            case registerTable:
                handleRegisterTable(exchange, service, config);
                break;
            case getAllColumns:
                handleGetAllColumns(exchange, service, config);
                break;
            case listPrestoEngines:
                handleListPrestoEngines(exchange, service, config);
                break;
            case getPrestoEngine:
                handleGetPrestoEngine(exchange, service, config);
                break;
            case listPrestissimoEngines:
                handleListPrestissimoEngines(exchange, service, config);
                break;
            case getPrestissimoEngine:
                handleGetPrestissimoEngine(exchange, service, config);
                break;
            case listStorageRegistrations:
                handleListStorageRegistrations(exchange, service, config);
                break;
            case createStorageRegistration:
                handleCreateStorageRegistration(exchange, service, config);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private void handleListCatalogs(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        ListCatalogsOptions.Builder builder = new ListCatalogsOptions.Builder();
        applyAuthInstanceId(builder, exchange, config);

        Response<CatalogCollection> response = service.listCatalogs(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleGetCatalog(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        requireNonEmpty(catalogName, "Catalog name");

        GetCatalogOptions.Builder builder = new GetCatalogOptions.Builder(catalogName);
        applyAuthInstanceId(builder, exchange, config);

        Response<Catalog> response = service.getCatalog(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleDeleteCatalog(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        requireNonEmpty(catalogName, "Catalog name");

        DeleteCatalogOptions.Builder builder = new DeleteCatalogOptions.Builder(catalogName);
        applyAuthInstanceId(builder, exchange, config);

        service.deleteCatalog(builder.build()).execute();
        exchange.getMessage().setBody(null);
    }

    private void handleListSchemas(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String engineId = resolveHeader(exchange, WatsonxDataConstants.ENGINE_ID, config.getEngineId());
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        requireNonEmpty(engineId, "Engine ID");
        requireNonEmpty(catalogName, "Catalog name");

        ListSchemasOptions.Builder builder = new ListSchemasOptions.Builder(engineId, catalogName);
        applyAuthInstanceId(builder, exchange, config);

        Response<SchemasCollection> response = service.listSchemas(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleCreateSchema(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String engineId = resolveHeader(exchange, WatsonxDataConstants.ENGINE_ID, config.getEngineId());
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        String schemaName = exchange.getIn().getHeader(WatsonxDataConstants.SCHEMA_NAME, String.class);
        String customPath = exchange.getIn().getHeader(WatsonxDataConstants.CUSTOM_PATH, String.class);
        requireNonEmpty(engineId, "Engine ID");
        requireNonEmpty(catalogName, "Catalog name");
        requireNonEmpty(schemaName, "Schema name");
        requireNonEmpty(customPath, "Custom path");

        CreateSchemaOptions.Builder builder
                = new CreateSchemaOptions.Builder(engineId, catalogName, customPath, schemaName);
        applyAuthInstanceId(builder, exchange, config);

        Response<SchemaPrototype> response = service.createSchema(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleDeleteSchema(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String engineId = resolveHeader(exchange, WatsonxDataConstants.ENGINE_ID, config.getEngineId());
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        String schemaName = resolveHeader(exchange, WatsonxDataConstants.SCHEMA_NAME, config.getSchemaName());
        requireNonEmpty(engineId, "Engine ID");
        requireNonEmpty(catalogName, "Catalog name");
        requireNonEmpty(schemaName, "Schema name");

        DeleteSchemaOptions.Builder builder = new DeleteSchemaOptions.Builder(engineId, catalogName, schemaName);
        applyAuthInstanceId(builder, exchange, config);

        service.deleteSchema(builder.build()).execute();
        exchange.getMessage().setBody(null);
    }

    private void handleListTables(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        String schemaName = resolveHeader(exchange, WatsonxDataConstants.SCHEMA_NAME, config.getSchemaName());
        String engineId = resolveHeader(exchange, WatsonxDataConstants.ENGINE_ID, config.getEngineId());
        requireNonEmpty(catalogName, "Catalog name");
        requireNonEmpty(schemaName, "Schema name");
        requireNonEmpty(engineId, "Engine ID");

        ListTablesOptions.Builder builder = new ListTablesOptions.Builder(catalogName, schemaName, engineId);
        applyAuthInstanceId(builder, exchange, config);

        Response<TableCollection> response = service.listTables(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleGetTable(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        String schemaName = resolveHeader(exchange, WatsonxDataConstants.SCHEMA_NAME, config.getSchemaName());
        String tableName = exchange.getIn().getHeader(WatsonxDataConstants.TABLE_NAME, String.class);
        String engineId = resolveHeader(exchange, WatsonxDataConstants.ENGINE_ID, config.getEngineId());
        requireNonEmpty(catalogName, "Catalog name");
        requireNonEmpty(schemaName, "Schema name");
        requireNonEmpty(tableName, "Table name");
        requireNonEmpty(engineId, "Engine ID");

        GetTableOptions.Builder builder = new GetTableOptions.Builder(catalogName, schemaName, tableName, engineId);
        applyAuthInstanceId(builder, exchange, config);

        Response<Table> response = service.getTable(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleDeleteTable(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        String schemaName = resolveHeader(exchange, WatsonxDataConstants.SCHEMA_NAME, config.getSchemaName());
        String tableName = exchange.getIn().getHeader(WatsonxDataConstants.TABLE_NAME, String.class);
        String engineId = resolveHeader(exchange, WatsonxDataConstants.ENGINE_ID, config.getEngineId());
        requireNonEmpty(catalogName, "Catalog name");
        requireNonEmpty(schemaName, "Schema name");
        requireNonEmpty(tableName, "Table name");
        requireNonEmpty(engineId, "Engine ID");

        DeleteTableOptions.Builder builder = new DeleteTableOptions.Builder(catalogName, schemaName, tableName, engineId);
        applyAuthInstanceId(builder, exchange, config);

        service.deleteTable(builder.build()).execute();
        exchange.getMessage().setBody(null);
    }

    @SuppressWarnings("unchecked")
    private void handleUpdateTable(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        String schemaName = resolveHeader(exchange, WatsonxDataConstants.SCHEMA_NAME, config.getSchemaName());
        String tableName = exchange.getIn().getHeader(WatsonxDataConstants.TABLE_NAME, String.class);
        String engineId = resolveHeader(exchange, WatsonxDataConstants.ENGINE_ID, config.getEngineId());
        requireNonEmpty(catalogName, "Catalog name");
        requireNonEmpty(schemaName, "Schema name");
        requireNonEmpty(tableName, "Table name");
        requireNonEmpty(engineId, "Engine ID");

        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("Request body with table patch data is required for updateTable operation");
        }

        UpdateTableOptions.Builder builder
                = new UpdateTableOptions.Builder(catalogName, schemaName, tableName, engineId, body);
        applyAuthInstanceId(builder, exchange, config);

        Response<Table> response = service.updateTable(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleRegisterTable(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String catalogId = exchange.getIn().getHeader(WatsonxDataConstants.CATALOG_ID, String.class);
        String schemaId = exchange.getIn().getHeader(WatsonxDataConstants.SCHEMA_ID, String.class);
        String metadataLocation = exchange.getIn().getHeader(WatsonxDataConstants.METADATA_LOCATION, String.class);
        String tableName = exchange.getIn().getHeader(WatsonxDataConstants.TABLE_NAME, String.class);
        requireNonEmpty(catalogId, "Catalog ID");
        requireNonEmpty(schemaId, "Schema ID");
        requireNonEmpty(metadataLocation, "Metadata location");
        requireNonEmpty(tableName, "Table name");

        RegisterTableOptions.Builder builder
                = new RegisterTableOptions.Builder(catalogId, schemaId, metadataLocation, tableName);
        applyAuthInstanceId(builder, exchange, config);

        Response<RegisterTableCreatedBody> response = service.registerTable(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleGetAllColumns(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String catalogName = resolveHeader(exchange, WatsonxDataConstants.CATALOG_NAME, config.getCatalogName());
        requireNonEmpty(catalogName, "Catalog name");

        GetAllColumnsOptions.Builder builder = new GetAllColumnsOptions.Builder(catalogName);

        String schemaName = resolveHeader(exchange, WatsonxDataConstants.SCHEMA_NAME, config.getSchemaName());
        if (schemaName != null && !schemaName.isEmpty()) {
            builder.schemaName(schemaName);
        }
        String tableName = exchange.getIn().getHeader(WatsonxDataConstants.TABLE_NAME, String.class);
        if (tableName != null && !tableName.isEmpty()) {
            builder.tableName(tableName);
        }
        applyAuthInstanceId(builder, exchange, config);

        Response<ColumnsCollection> response = service.getAllColumns(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleListPrestoEngines(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        ListPrestoEnginesOptions.Builder builder = new ListPrestoEnginesOptions.Builder();
        applyAuthInstanceId(builder, exchange, config);

        Response<PrestoEngineCollection> response = service.listPrestoEngines(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleGetPrestoEngine(Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String engineId = resolveHeader(exchange, WatsonxDataConstants.ENGINE_ID, config.getEngineId());
        requireNonEmpty(engineId, "Engine ID");

        GetPrestoEngineOptions.Builder builder = new GetPrestoEngineOptions.Builder(engineId);
        applyAuthInstanceId(builder, exchange, config);

        Response<PrestoEngine> response = service.getPrestoEngine(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleListPrestissimoEngines(
            Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        ListPrestissimoEnginesOptions.Builder builder = new ListPrestissimoEnginesOptions.Builder();
        applyAuthInstanceId(builder, exchange, config);

        Response<PrestissimoEngineCollection> response = service.listPrestissimoEngines(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleGetPrestissimoEngine(
            Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String engineId = resolveHeader(exchange, WatsonxDataConstants.ENGINE_ID, config.getEngineId());
        requireNonEmpty(engineId, "Engine ID");

        GetPrestissimoEngineOptions.Builder builder = new GetPrestissimoEngineOptions.Builder(engineId);
        applyAuthInstanceId(builder, exchange, config);

        Response<PrestissimoEngine> response = service.getPrestissimoEngine(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleListStorageRegistrations(
            Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        ListStorageRegistrationsOptions.Builder builder = new ListStorageRegistrationsOptions.Builder();
        applyAuthInstanceId(builder, exchange, config);

        Response<StorageRegistrationCollection> response = service.listStorageRegistrations(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private void handleCreateStorageRegistration(
            Exchange exchange, WatsonxData service, WatsonxDataConfiguration config) {
        String description = exchange.getIn().getHeader(WatsonxDataConstants.STORAGE_DESCRIPTION, String.class);
        String displayName = exchange.getIn().getHeader(WatsonxDataConstants.STORAGE_DISPLAY_NAME, String.class);
        String managedBy = exchange.getIn().getHeader(WatsonxDataConstants.STORAGE_MANAGED_BY, String.class);
        String storageType = exchange.getIn().getHeader(WatsonxDataConstants.STORAGE_TYPE, String.class);
        requireNonEmpty(description, "Storage description");
        requireNonEmpty(displayName, "Storage display name");
        requireNonEmpty(managedBy, "Storage managed by");
        requireNonEmpty(storageType, "Storage type");

        CreateStorageRegistrationOptions.Builder builder
                = new CreateStorageRegistrationOptions.Builder(description, displayName, managedBy, storageType);
        applyAuthInstanceId(builder, exchange, config);

        Response<StorageRegistration> response = service.createStorageRegistration(builder.build()).execute();
        exchange.getMessage().setBody(response.getResult());
    }

    private WatsonxDataOperations determineOperation(Exchange exchange) {
        WatsonxDataOperations operation = exchange.getIn().getHeader(
                WatsonxDataConstants.OPERATION, WatsonxDataOperations.class);

        if (operation == null) {
            operation = getEndpoint().getConfiguration().getOperation();
        }

        if (operation == null) {
            throw new IllegalArgumentException(
                    "Operation must be specified either via header '" + WatsonxDataConstants.OPERATION
                                               + "' or endpoint configuration 'operation'");
        }

        return operation;
    }

    private String resolveHeader(Exchange exchange, String headerName, String configDefault) {
        String value = exchange.getIn().getHeader(headerName, String.class);
        return value != null ? value : configDefault;
    }

    private void requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " is required for this operation");
        }
    }

    private void applyAuthInstanceId(Object builder, Exchange exchange, WatsonxDataConfiguration config) {
        String authInstanceId = exchange.getIn().getHeader(WatsonxDataConstants.AUTH_INSTANCE_ID, String.class);
        if (authInstanceId == null) {
            authInstanceId = config.getAuthInstanceId();
        }
        if (authInstanceId != null) {
            try {
                builder.getClass().getMethod("authInstanceId", String.class).invoke(builder, authInstanceId);
            } catch (Exception e) {
                LOG.debug("authInstanceId not supported on builder: {}", builder.getClass().getSimpleName());
            }
        }
    }

    @Override
    public WatsonxDataEndpoint getEndpoint() {
        return (WatsonxDataEndpoint) super.getEndpoint();
    }
}
