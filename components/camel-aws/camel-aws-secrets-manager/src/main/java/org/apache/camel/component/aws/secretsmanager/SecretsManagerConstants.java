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
package org.apache.camel.component.aws.secretsmanager;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Secrets Manager module
 */
public interface SecretsManagerConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsSecretsManagerOperation";
    @Metadata(description = "The number of results to include in the response.", javaType = "Integer")
    String MAX_RESULTS = "CamelAwsSecretsManagerMaxResults";
    @Metadata(description = "The name of the secret.", javaType = "String")
    String SECRET_NAME = "CamelAwsSecretsManagerSecretName";
    @Metadata(description = "The description of the secret.", javaType = "String")
    String SECRET_DESCRIPTION = "CamelAwsSecretsManagerSecretDescription";
    @Metadata(description = "The ARN or name of the secret.", javaType = "String")
    String SECRET_ID = "CamelAwsSecretsManagerSecretId";
    @Metadata(description = "The ARN of the Lambda rotation function that can rotate the secret.", javaType = "String")
    String LAMBDA_ROTATION_FUNCTION_ARN = "CamelAwsSecretsManagerLambdaRotationFunctionArn";
    @Metadata(description = "The unique identifier of the version of the secret.", javaType = "String")
    String SECRET_VERSION_ID = "CamelAwsSecretsManagerSecretVersionId";
    @Metadata(description = "A comma separated list of Regions in which to replicate the secret.", javaType = "String")
    String SECRET_REPLICATION_REGIONS = "CamelAwsSecretsManagerSecretReplicationRegions";
}
