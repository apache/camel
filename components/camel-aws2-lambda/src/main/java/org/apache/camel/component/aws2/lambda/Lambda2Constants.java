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
package org.apache.camel.component.aws2.lambda;

/**
 * Constants used in Camel AWS Lambda module
 */
public interface Lambda2Constants {

    String OPERATION = "CamelAwsLambdaOperation";

    String S3_BUCKET = "CamelAwsLambdaS3Bucket";
    String S3_KEY = "CamelAwsLambdaS3Key";
    String S3_OBJECT_VERSION = "CamelAwsLambdaS3ObjectVersion";
    String ZIP_FILE = "CamelAwsLambdaZipFile";
    String DESCRIPTION = "CamelAwsLambdaDescription";
    String ROLE = "CamelAwsLambdaRole";
    String RUNTIME = "CamelAwsLambdaRuntime";
    String HANDLER = "CamelAwsLambdaHandler";
    String TARGET_ARN = "CamelAwsLambdaTargetArn";
    String MEMORY_SIZE = "CamelAwsLambdaMemorySize";
    String KMS_KEY_ARN = "CamelAwsLambdaKMSKeyArn";
    String ENVIRONMENT_VARIABLES = "CamelAwsLambdaEnvironmentVariables";
    String PUBLISH = "CamelAwsLambdaPublish";
    String TIMEOUT = "CamelAwsLambdaTimeout";
    String TAGS = "CamelAwsLambdaTags";
    String TRACING_CONFIG = "CamelAwsLambdaTracingConfig";
    String SECURITY_GROUP_IDS = "CamelAwsLambdaSecurityGroupIds";
    String SUBNET_IDS = "CamelAwsLambdaSubnetIds";
    String EVENT_SOURCE_ARN = "CamelAwsLambdaEventSourceArn";
    String EVENT_SOURCE_BATCH_SIZE = "CamelAwsLambdaEventSourceBatchSize";
    String EVENT_SOURCE_UUID = "CamelAwsLambdaEventSourceUuid";
    String RESOURCE_ARN = "CamelAwsLambdaResourceArn";
    String RESOURCE_TAGS = "CamelAwsLambdaResourceTags";
    String RESOURCE_TAG_KEYS = "CamelAwsLambdaResourceTagKeys";
    String VERSION_DESCRIPTION = "CamelAwsLambdaVersionDescription";
    String VERSION_REVISION_ID = "CamelAwsLambdaVersionRevisionId";
    String FUNCTION_VERSION = "CamelAwsLambdaFunctionVersion";
    String FUNCTION_ALIAS_NAME = "CamelAwsLambdaAliasFunctionName";
    String FUNCTION_ALIAS_DESCRIPTION = "CamelAwsLambdaAliasFunctionDescription";
}
