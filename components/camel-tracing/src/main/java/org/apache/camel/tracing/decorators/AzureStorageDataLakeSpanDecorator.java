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
package org.apache.camel.tracing.decorators;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.TagConstants;

public class AzureStorageDataLakeSpanDecorator extends AbstractSpanDecorator {

    static final String STORAGE_DATALAKE_DIRECTORY_NAME = "directoryName";
    static final String STORAGE_DATALAKE_FILE_NAME = "fileName";
    static final String STORAGE_DATALAKE_PATH = "path";
    static final String STORAGE_DATALAKE_TIMEOUT = "timeout";
    static final String STORAGE_DATALAKE_CONTENT_TYPE = "contentType";
    static final String STORAGE_DATALAKE_METADATA = "metadata";
    static final String STORAGE_DATALAKE_LAST_MODIFIED = "lastModified";
    static final String STORAGE_DATALAKE_POSITION = "position";
    static final String STORAGE_DATALAKE_EXPRESSION = "expression";

    /**
     * Constants copied from {@link org.apache.camel.component.azure.storage.datalake.DataLakeConstants}
     */
    static final String OPERATION = "CamelAzureStorageDataLakeOperation";
    static final String FILESYSTEM_NAME = "CamelAzureStorageDataLakeFileSystemName";
    static final String DIRECTORY_NAME = "CamelAzureStorageDataLakeDirectoryName";
    static final String FILE_NAME = "CamelAzureStorageDataLakeFileName";
    static final String PATH = "CamelAzureStorageDataLakePath";
    static final String TIMEOUT = "CamelAzureStorageDataLakeTimeout";
    static final String CONTENT_TYPE = "CamelAzureStorageDataLakeContentType";
    static final String METADATA = "CamelAzureStorageDataLakeMetadata";
    static final String LAST_MODIFIED = "CamelAzureStorageDataLakeLastModified";
    static final String POSITION = "CamelAzureStorageDataLakePosition";
    static final String EXPRESSION = "CamelAzureStorageDataLakeExpression";

    @Override
    public String getComponent() {
        return "azure-storage-datalake";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.azure.storage.datalake.DataLakeComponent";
    }

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation == null) {
            Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
            return queryParameters.containsKey("operation")
                    ? queryParameters.get("operation")
                    : super.getOperationName(exchange, endpoint);
        }
        return operation;
    }

    @Override
    public void pre(SpanAdapter span, Exchange exchange, Endpoint endpoint) {
        super.pre(span, exchange, endpoint);
        span.setTag(TagConstants.DB_SYSTEM, getComponent());

        String fileSystemName = exchange.getIn().getHeader(FILESYSTEM_NAME, String.class);
        if (fileSystemName != null) {
            span.setTag(TagConstants.DB_NAME, fileSystemName);
        }

        String directoryName = exchange.getIn().getHeader(DIRECTORY_NAME, String.class);
        if (directoryName != null) {
            span.setTag(STORAGE_DATALAKE_DIRECTORY_NAME, directoryName);
        }

        String fileName = exchange.getIn().getHeader(FILE_NAME, String.class);
        if (fileName != null) {
            span.setTag(STORAGE_DATALAKE_FILE_NAME, fileName);
        }

        String path = exchange.getIn().getHeader(PATH, String.class);
        if (path != null) {
            span.setTag(STORAGE_DATALAKE_PATH, path);
        }

        Duration timeout = exchange.getIn().getHeader(TIMEOUT, Duration.class);
        if (timeout != null) {
            span.setTag(STORAGE_DATALAKE_TIMEOUT, timeout.toString());
        }

        String contentType = exchange.getIn().getHeader(CONTENT_TYPE, String.class);
        if (contentType != null) {
            span.setTag(STORAGE_DATALAKE_CONTENT_TYPE, contentType);
        }

        Map metadata = exchange.getIn().getHeader(METADATA, Map.class);
        if (metadata != null) {
            span.setTag(STORAGE_DATALAKE_METADATA, metadata.toString());
        }

        OffsetDateTime lastModified = exchange.getIn().getHeader(LAST_MODIFIED, OffsetDateTime.class);
        if (lastModified != null) {
            span.setTag(STORAGE_DATALAKE_LAST_MODIFIED, lastModified.toString());
        }

        Long position = exchange.getIn().getHeader(POSITION, Long.class);
        if (position != null) {
            span.setTag(STORAGE_DATALAKE_POSITION, position);
        }

        String expression = exchange.getIn().getHeader(EXPRESSION, String.class);
        if (expression != null) {
            span.setTag(STORAGE_DATALAKE_EXPRESSION, expression);
        }
    }

}
