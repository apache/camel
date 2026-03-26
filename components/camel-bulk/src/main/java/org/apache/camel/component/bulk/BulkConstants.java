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
package org.apache.camel.component.bulk;

import org.apache.camel.spi.Metadata;

/**
 * Constants for the Bulk component headers.
 */
public final class BulkConstants {

    @Metadata(description = "The bulk job name", javaType = "String")
    public static final String BULK_JOB_NAME = "CamelBulkJobName";
    @Metadata(description = "Unique identifier for this bulk job execution", javaType = "String")
    public static final String BULK_JOB_INSTANCE_ID = "CamelBulkJobInstanceId";
    @Metadata(description = "Total items processed", javaType = "int")
    public static final String BULK_TOTAL = "CamelBulkTotal";
    @Metadata(description = "Successfully processed items", javaType = "int")
    public static final String BULK_SUCCESS = "CamelBulkSuccess";
    @Metadata(description = "Failed items", javaType = "int")
    public static final String BULK_FAILED = "CamelBulkFailed";
    @Metadata(description = "Total processing time in milliseconds", javaType = "long")
    public static final String BULK_DURATION = "CamelBulkDuration";
    @Metadata(description = "Whether the bulk operation was aborted due to error threshold", javaType = "boolean")
    public static final String BULK_ABORTED = "CamelBulkAborted";
    @Metadata(description = "Zero-based index of the current item", javaType = "int")
    public static final String BULK_INDEX = "CamelBulkIndex";
    @Metadata(description = "Total number of items in the bulk operation", javaType = "int")
    public static final String BULK_SIZE = "CamelBulkSize";
    @Metadata(description = "Index of the current chunk", javaType = "int")
    public static final String BULK_CHUNK_INDEX = "CamelBulkChunkIndex";
    @Metadata(description = "Index of the current step in a multi-step bulk operation", javaType = "int")
    public static final String BULK_STEP_INDEX = "CamelBulkStepIndex";
    @Metadata(description = "The current watermark value from the watermark store", javaType = "String")
    public static final String BULK_WATERMARK_VALUE = "CamelBulkWatermarkValue";

    private BulkConstants() {
    }
}
