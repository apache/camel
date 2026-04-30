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

import org.apache.camel.spi.Metadata;

/**
 * Constants for IBM watsonx.data component headers.
 */
public interface WatsonxDataConstants {

    String HEADER_PREFIX = "CamelIBMWatsonxData";

    // Operation
    @Metadata(description = "The operation to perform", javaType = "WatsonxDataOperations")
    String OPERATION = HEADER_PREFIX + "Operation";

    // Catalog
    @Metadata(description = "The catalog name", javaType = "String")
    String CATALOG_NAME = HEADER_PREFIX + "CatalogName";

    // Schema
    @Metadata(description = "The schema name", javaType = "String")
    String SCHEMA_NAME = HEADER_PREFIX + "SchemaName";

    @Metadata(description = "The custom path for schema creation", javaType = "String")
    String CUSTOM_PATH = HEADER_PREFIX + "CustomPath";

    // Table
    @Metadata(description = "The table name", javaType = "String")
    String TABLE_NAME = HEADER_PREFIX + "TableName";

    @Metadata(description = "The metadata location for table registration (e.g., S3 path to Iceberg metadata)",
              javaType = "String")
    String METADATA_LOCATION = HEADER_PREFIX + "MetadataLocation";

    @Metadata(description = "The catalog ID for table registration", javaType = "String")
    String CATALOG_ID = HEADER_PREFIX + "CatalogId";

    @Metadata(description = "The schema ID for table registration", javaType = "String")
    String SCHEMA_ID = HEADER_PREFIX + "SchemaId";

    // Engine
    @Metadata(description = "The engine ID", javaType = "String")
    String ENGINE_ID = HEADER_PREFIX + "EngineId";

    // Storage Registration
    @Metadata(description = "The storage description for registration", javaType = "String")
    String STORAGE_DESCRIPTION = HEADER_PREFIX + "StorageDescription";

    @Metadata(description = "The storage display name for registration", javaType = "String")
    String STORAGE_DISPLAY_NAME = HEADER_PREFIX + "StorageDisplayName";

    @Metadata(description = "The storage managed by value (e.g., ibm, customer)", javaType = "String")
    String STORAGE_MANAGED_BY = HEADER_PREFIX + "StorageManagedBy";

    @Metadata(description = "The storage type (e.g., ibm_cos, aws_s3, google_cs)", javaType = "String")
    String STORAGE_TYPE = HEADER_PREFIX + "StorageType";

    // Auth header
    @Metadata(description = "The auth instance ID for watsonx.data API calls", javaType = "String")
    String AUTH_INSTANCE_ID = HEADER_PREFIX + "AuthInstanceId";
}
