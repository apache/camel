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

/**
 * Operations supported by the Azure Functions component.
 */
public enum FunctionsOperations {

    // Invocation operations
    /**
     * Invoke an HTTP-triggered Azure Function (default operation)
     */
    invokeFunction,

    // Function App management operations
    /**
     * List all function apps in subscription or resource group
     */
    listFunctionApps,

    /**
     * Get function app details
     */
    getFunctionApp,

    /**
     * Create a new function app
     */
    createFunctionApp,

    /**
     * Delete a function app
     */
    deleteFunctionApp,

    /**
     * Start a stopped function app
     */
    startFunctionApp,

    /**
     * Stop a running function app
     */
    stopFunctionApp,

    /**
     * Restart a function app
     */
    restartFunctionApp,

    // Function-level operations
    /**
     * List functions within a function app
     */
    listFunctions,

    /**
     * Get function details
     */
    getFunction,

    /**
     * Get function keys (for invocation)
     */
    getFunctionKeys,

    // Configuration operations
    /**
     * Get function app configuration/settings
     */
    getFunctionAppConfiguration,

    /**
     * Update function app configuration/settings
     */
    updateFunctionAppConfiguration,

    // Tag operations
    /**
     * List tags on the function app resource
     */
    listTags,

    /**
     * Add tags to the function app resource
     */
    tagResource,

    /**
     * Remove tags from the function app resource
     */
    untagResource
}
