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
 * Determines the credential strategy to use for Azure Functions operations.
 */
public enum CredentialType {
    /**
     * Uses DefaultAzureCredential for automatic credential chain. Supports environment variables, managed identity,
     * Azure CLI, etc.
     */
    AZURE_IDENTITY,

    /**
     * Uses function key or host key for direct HTTP invocation. Only valid for invokeFunction operation.
     */
    FUNCTION_KEY,

    /**
     * Uses a provided TokenCredential instance.
     */
    TOKEN_CREDENTIAL
}
