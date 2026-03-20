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
package org.apache.camel.component.batch;

import org.apache.camel.spi.Metadata;

/**
 * Constants for the Batch component headers.
 */
public final class BatchConstants {

    @Metadata(description = "The batch job name", javaType = "String")
    public static final String BATCH_JOB_NAME = "CamelBatchJobName";
    @Metadata(description = "Total items processed", javaType = "int")
    public static final String BATCH_TOTAL = "CamelBatchTotal";
    @Metadata(description = "Successfully processed items", javaType = "int")
    public static final String BATCH_SUCCESS = "CamelBatchSuccess";
    @Metadata(description = "Failed items", javaType = "int")
    public static final String BATCH_FAILED = "CamelBatchFailed";
    @Metadata(description = "Total processing time in milliseconds", javaType = "long")
    public static final String BATCH_DURATION = "CamelBatchDuration";
    @Metadata(description = "Whether the batch was aborted due to error threshold", javaType = "boolean")
    public static final String BATCH_ABORTED = "CamelBatchAborted";
    @Metadata(description = "Zero-based index of the current item", javaType = "int")
    public static final String BATCH_INDEX = "CamelBatchIndex";
    @Metadata(description = "Total number of items in the batch", javaType = "int")
    public static final String BATCH_SIZE = "CamelBatchSize";
    @Metadata(description = "Index of the current chunk", javaType = "int")
    public static final String BATCH_CHUNK_INDEX = "CamelBatchChunkIndex";

    private BatchConstants() {
    }
}
