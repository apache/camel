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
package org.apache.camel.component.aws.parameterstore;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Parameter Store module
 */
public interface ParameterStoreConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsParameterStoreOperation";
    @Metadata(description = "The number of results to include in the response.", javaType = "Integer")
    String MAX_RESULTS = "CamelAwsParameterStoreMaxResults";
    @Metadata(description = "The name of the parameter.", javaType = "String")
    String PARAMETER_NAME = "CamelAwsParameterStoreName";
    @Metadata(description = "A comma separated list of parameter names.", javaType = "String")
    String PARAMETER_NAMES = "CamelAwsParameterStoreNames";
    @Metadata(description = "The description of the parameter.", javaType = "String")
    String PARAMETER_DESCRIPTION = "CamelAwsParameterStoreDescription";
    @Metadata(description = "The value of the parameter.", javaType = "String")
    String PARAMETER_VALUE = "CamelAwsParameterStoreValue";
    @Metadata(description = "The type of the parameter (String, StringList, SecureString).", javaType = "String")
    String PARAMETER_TYPE = "CamelAwsParameterStoreType";
    @Metadata(description = "The hierarchy path for the parameter.", javaType = "String")
    String PARAMETER_PATH = "CamelAwsParameterStorePath";
    @Metadata(description = "Whether to decrypt SecureString values.", javaType = "Boolean")
    String WITH_DECRYPTION = "CamelAwsParameterStoreWithDecryption";
    @Metadata(description = "Whether to retrieve all parameters within a hierarchy recursively.", javaType = "Boolean")
    String RECURSIVE = "CamelAwsParameterStoreRecursive";
    @Metadata(description = "Whether to overwrite an existing parameter.", javaType = "Boolean")
    String OVERWRITE = "CamelAwsParameterStoreOverwrite";
    @Metadata(description = "The version of the parameter.", javaType = "Long")
    String PARAMETER_VERSION = "CamelAwsParameterStoreVersion";
    @Metadata(description = "The KMS Key ID to use for SecureString encryption.", javaType = "String")
    String KMS_KEY_ID = "CamelAwsParameterStoreKmsKeyId";
    @Metadata(description = "The tier for the parameter (Standard, Advanced, Intelligent-Tiering).", javaType = "String")
    String PARAMETER_TIER = "CamelAwsParameterStoreTier";
}
