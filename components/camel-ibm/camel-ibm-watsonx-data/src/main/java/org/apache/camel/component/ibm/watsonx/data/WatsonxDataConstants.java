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
    @Metadata(description = "The operation to perform. Overrides the operation option.",
              javaType = "org.apache.camel.component.ibm.watsonx.data.WatsonxDataOperations")
    String OPERATION = HEADER_PREFIX + "Operation";

    // Catalog
    @Metadata(description = "The name of the catalog. Overrides the catalogName option.", javaType = "String")
    String CATALOG_NAME = HEADER_PREFIX + "CatalogName";

    // Schema
    @Metadata(description = "The name of the schema. Overrides the schemaName option. createSchema takes the"
                            + " name of the new schema from this header only.",
              javaType = "String")
    String SCHEMA_NAME = HEADER_PREFIX + "SchemaName";

    @Metadata(description = "The path within the storage bucket where createSchema creates the schema."
                            + " Required for createSchema.",
              javaType = "String")
    String CUSTOM_PATH = HEADER_PREFIX + "CustomPath";

    // Table
    @Metadata(description = "The name of the table. Required for getTable, deleteTable, updateTable and"
                            + " registerTable; getAllColumns returns only the columns of this table. There is no"
                            + " option for it.",
              javaType = "String")
    String TABLE_NAME = HEADER_PREFIX + "TableName";

    @Metadata(description = "The location of the metadata file of the table that registerTable registers, for"
                            + " example the S3 path of an Iceberg metadata file. Required for registerTable.",
              javaType = "String")
    String METADATA_LOCATION = HEADER_PREFIX + "MetadataLocation";

    @Metadata(description = "The ID of the catalog that registerTable registers the table in. Required for"
                            + " registerTable, which ignores the catalogName option.",
              javaType = "String")
    String CATALOG_ID = HEADER_PREFIX + "CatalogId";

    @Metadata(description = "The ID of the schema that registerTable registers the table in. Required for"
                            + " registerTable, which ignores the schemaName option.",
              javaType = "String")
    String SCHEMA_ID = HEADER_PREFIX + "SchemaId";

    // Engine
    @Metadata(description = "The ID of the engine. Overrides the engineId option.", javaType = "String")
    String ENGINE_ID = HEADER_PREFIX + "EngineId";

    // Storage Registration
    @Metadata(description = "The description of the storage that createStorageRegistration registers."
                            + " Required for createStorageRegistration.",
              javaType = "String")
    String STORAGE_DESCRIPTION = HEADER_PREFIX + "StorageDescription";

    @Metadata(description = "The display name of the storage that createStorageRegistration registers."
                            + " Required for createStorageRegistration.",
              javaType = "String")
    String STORAGE_DISPLAY_NAME = HEADER_PREFIX + "StorageDisplayName";

    @Metadata(description = "Who manages the storage that createStorageRegistration registers: ibm or"
                            + " customer. Required for createStorageRegistration.",
              javaType = "String")
    String STORAGE_MANAGED_BY = HEADER_PREFIX + "StorageManagedBy";

    @Metadata(description = "The type of the storage that createStorageRegistration registers, such as"
                            + " ibm_cos, aws_s3, s3, minio, ibm_ceph, adls_gen1, adls_gen2, google_cs, ozone or"
                            + " ibm_storage_scale. Required for createStorageRegistration.",
              javaType = "String")
    String STORAGE_TYPE = HEADER_PREFIX + "StorageType";

    // Auth header
    @Metadata(description = "The ID of the watsonx.data instance, sent in the AuthInstanceId HTTP header."
                            + " Overrides the authInstanceId option.",
              javaType = "String")
    String AUTH_INSTANCE_ID = HEADER_PREFIX + "AuthInstanceId";
}
