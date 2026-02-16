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
package org.apache.camel.component.azure.functions;

import org.apache.camel.spi.Metadata;

/**
 * Constants for Azure Functions component headers.
 */
public final class FunctionsConstants {

    private static final String HEADER_PREFIX = "CamelAzureFunctions";

    private FunctionsConstants() {
    }

    // Operation override header
    @Metadata(label = "producer",
              description = "The operation to perform. Overrides the operation in the endpoint.",
              javaType = "org.apache.camel.component.azure.functions.FunctionsOperations")
    public static final String OPERATION = HEADER_PREFIX + "Operation";

    // Common headers
    @Metadata(label = "producer",
              description = "The function app name",
              javaType = "String")
    public static final String FUNCTION_APP = HEADER_PREFIX + "FunctionApp";

    @Metadata(label = "producer",
              description = "The function name within the app",
              javaType = "String")
    public static final String FUNCTION_NAME = HEADER_PREFIX + "FunctionName";

    @Metadata(label = "producer",
              description = "The resource group name",
              javaType = "String")
    public static final String RESOURCE_GROUP = HEADER_PREFIX + "ResourceGroup";

    // HTTP invocation headers
    @Metadata(label = "producer",
              description = "The HTTP method for function invocation (GET, POST, PUT, DELETE, etc.)",
              javaType = "String")
    public static final String HTTP_METHOD = HEADER_PREFIX + "HttpMethod";

    @Metadata(label = "producer",
              description = "Custom HTTP headers for function invocation",
              javaType = "Map<String, String>")
    public static final String HTTP_HEADERS = HEADER_PREFIX + "HttpHeaders";

    // Response headers
    @Metadata(label = "producer",
              description = "The HTTP status code from the response",
              javaType = "Integer")
    public static final String STATUS_CODE = HEADER_PREFIX + "StatusCode";

    @Metadata(label = "producer",
              description = "The response headers from function invocation",
              javaType = "Map<String, List<String>>")
    public static final String RESPONSE_HEADERS = HEADER_PREFIX + "ResponseHeaders";

    // Function app details
    @Metadata(label = "producer",
              description = "The function app state (Running, Stopped, etc.)",
              javaType = "String")
    public static final String FUNCTION_APP_STATE = HEADER_PREFIX + "FunctionAppState";

    @Metadata(label = "producer",
              description = "The function app resource ID",
              javaType = "String")
    public static final String RESOURCE_ID = HEADER_PREFIX + "ResourceId";

    @Metadata(label = "producer",
              description = "The default hostname of the function app",
              javaType = "String")
    public static final String DEFAULT_HOSTNAME = HEADER_PREFIX + "DefaultHostname";

    // Configuration headers
    @Metadata(label = "producer",
              description = "App settings to update (for updateFunctionAppConfiguration)",
              javaType = "Map<String, String>")
    public static final String APP_SETTINGS = HEADER_PREFIX + "AppSettings";

    // Tag operation headers
    @Metadata(label = "producer",
              description = "Tags to apply to the resource (for tagResource)",
              javaType = "Map<String, String>")
    public static final String RESOURCE_TAGS = HEADER_PREFIX + "ResourceTags";

    @Metadata(label = "producer",
              description = "Tag keys to remove from the resource (for untagResource)",
              javaType = "List<String>")
    public static final String TAG_KEYS = HEADER_PREFIX + "TagKeys";

    // Function creation headers
    @Metadata(label = "producer",
              description = "Azure region for creating the function app (e.g., eastus, westeurope)",
              javaType = "String")
    public static final String LOCATION = HEADER_PREFIX + "Location";

    @Metadata(label = "producer",
              description = "Runtime stack for the function app (java, node, python, dotnet)",
              javaType = "String")
    public static final String RUNTIME = HEADER_PREFIX + "Runtime";

    @Metadata(label = "producer",
              description = "Runtime version for the function app",
              javaType = "String")
    public static final String RUNTIME_VERSION = HEADER_PREFIX + "RuntimeVersion";

    @Metadata(label = "producer",
              description = "Storage account connection string for the function app",
              javaType = "String")
    public static final String STORAGE_CONNECTION_STRING = HEADER_PREFIX + "StorageConnectionString";
}
