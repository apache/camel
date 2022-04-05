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

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Lambda module
 */
public interface Lambda2Constants {

    @Metadata(label = "all", description = "The operation we want to perform. Override operation passed as query parameter",
              javaType = "String", required = true)
    String OPERATION = "CamelAwsLambdaOperation";
    @Metadata(label = "createFunction", description = "Amazon S3 bucket name where the .zip file containing\n" +
                                                      "your deployment package is stored. This bucket must reside in the same AWS region where you are creating the Lambda function.",
              javaType = "String")
    String S3_BUCKET = "CamelAwsLambdaS3Bucket";
    @Metadata(label = "createFunction", description = "The Amazon S3 object (the deployment package) key name\n" +
                                                      "you want to upload.",
              javaType = "String")
    String S3_KEY = "CamelAwsLambdaS3Key";
    @Metadata(label = "createFunction", description = "The Amazon S3 object (the deployment package) version\n" +
                                                      "you want to upload.",
              javaType = "String")
    String S3_OBJECT_VERSION = "CamelAwsLambdaS3ObjectVersion";
    @Metadata(label = "createFunction", description = "The local path of the zip file (the deployment package).\n" +
                                                      " Content of zip file can also be put in Message body.",
              javaType = "String")
    String ZIP_FILE = "CamelAwsLambdaZipFile";
    @Metadata(label = "createFunction", description = "The user-provided description.", javaType = "String")
    String DESCRIPTION = "CamelAwsLambdaDescription";
    @Metadata(label = "createFunction", description = "The Amazon Resource Name (ARN) of the IAM role that Lambda assumes\n" +
                                                      " when it executes your function to access any other Amazon Web Services (AWS) resources.",
              javaType = "String", required = true)
    String ROLE = "CamelAwsLambdaRole";
    @Metadata(label = "createFunction", description = "The runtime environment for the Lambda function you are uploading.\n" +
                                                      " (nodejs, nodejs4.3, nodejs6.10, java8, python2.7, python3.6, dotnetcore1.0, odejs4.3-edge)",
              javaType = "String", required = true)
    String RUNTIME = "CamelAwsLambdaRuntime";
    @Metadata(label = "createFunction", description = "The function within your code that Lambda calls to begin execution.\n" +
                                                      " For Node.js, it is the module-name.export value in your function.\n" +
                                                      " For Java, it can be package.class-name::handler or package.class-name.",
              javaType = "String", required = true)
    String HANDLER = "CamelAwsLambdaHandler";
    @Metadata(label = "createFunction",
              description = "The parent object that contains the target ARN (Amazon Resource Name)\n" +
                            "of an Amazon SQS queue or Amazon SNS topic.",
              javaType = "String")
    String TARGET_ARN = "CamelAwsLambdaTargetArn";
    @Metadata(label = "createFunction", description = "The memory size, in MB, you configured for the function.\n" +
                                                      "Must be a multiple of 64 MB.",
              javaType = "Integer")
    String MEMORY_SIZE = "CamelAwsLambdaMemorySize";
    @Metadata(label = "createFunction",
              description = "The Amazon Resource Name (ARN) of the KMS key used to encrypt your function's environment variables.\n"
                            +
                            "If not provided, AWS Lambda will use a default service key.",
              javaType = "String")
    String KMS_KEY_ARN = "CamelAwsLambdaKMSKeyArn";
    @Metadata(label = "createFunction",
              description = "The key-value pairs that represent your environment's configuration settings.",
              javaType = "Map<String, String>")
    String ENVIRONMENT_VARIABLES = "CamelAwsLambdaEnvironmentVariables";
    @Metadata(label = "createFunction updateFunction",
              description = "This boolean parameter can be used to request AWS Lambda\n" +
                            "to create the Lambda function and publish a version as an atomic operation.",
              javaType = "Boolean")
    String PUBLISH = "CamelAwsLambdaPublish";
    @Metadata(label = "createFunction",
              description = "The function execution time at which Lambda should terminate the function.\n" +
                            "The default is 3 seconds.",
              javaType = "Integer")
    String TIMEOUT = "CamelAwsLambdaTimeout";
    @Metadata(label = "createFunction", description = "The list of tags (key-value pairs) assigned to the new function.",
              javaType = "Map<String, String>")
    String TAGS = "CamelAwsLambdaTags";
    @Metadata(label = "createFunction", description = "Your function's tracing settings (Active or PassThrough).",
              javaType = "String")
    String TRACING_CONFIG = "CamelAwsLambdaTracingConfig";
    @Metadata(label = "createFunction",
              description = "If your Lambda function accesses resources in a VPC, a list of one or more security groups IDs in your VPC.",
              javaType = "List<String>")
    String SECURITY_GROUP_IDS = "CamelAwsLambdaSecurityGroupIds";
    @Metadata(label = "createFunction",
              description = "If your Lambda function accesses resources in a VPC, a list of one or more subnet IDs in your VPC.",
              javaType = "List<String>")
    String SUBNET_IDS = "CamelAwsLambdaSubnetIds";
    @Metadata(label = "createEventSourceMapping", description = "The Amazon Resource Name (ARN) of the event source.",
              javaType = "String")
    String EVENT_SOURCE_ARN = "CamelAwsLambdaEventSourceArn";
    @Metadata(label = "createEventSourceMapping",
              description = "The maximum number of records in each batch that Lambda pulls from your stream or queue and sends to your function. ",
              javaType = "Integer")
    String EVENT_SOURCE_BATCH_SIZE = "CamelAwsLambdaEventSourceBatchSize";
    @Metadata(label = "deleteEventSourceMapping", description = "The identifier of the event source mapping.",
              javaType = "String")
    String EVENT_SOURCE_UUID = "CamelAwsLambdaEventSourceUuid";
    @Metadata(label = "listTags tagResource untagResource", description = "The function's Amazon Resource Name (ARN).",
              javaType = "String")
    String RESOURCE_ARN = "CamelAwsLambdaResourceArn";
    @Metadata(label = "tagResource", description = "A list of tags to apply to the function.", javaType = "Map<String, String>")
    String RESOURCE_TAGS = "CamelAwsLambdaResourceTags";
    @Metadata(label = "untagResource", description = "A list of tag keys to remove from the function.",
              javaType = "List<String>")
    String RESOURCE_TAG_KEYS = "CamelAwsLambdaResourceTagKeys";
    @Metadata(label = "publishVersion",
              description = "A description for the version to override the description in the function configuration.",
              javaType = "String")
    String VERSION_DESCRIPTION = "CamelAwsLambdaVersionDescription";
    @Metadata(label = "publishVersion",
              description = "Only update the function if the revision ID matches the ID that's specified.", javaType = "String")
    String VERSION_REVISION_ID = "CamelAwsLambdaVersionRevisionId";
    @Metadata(label = "createAlias listAliases", description = "The function version to set in the alias", javaType = "String")
    String FUNCTION_VERSION = "CamelAwsLambdaFunctionVersion";
    @Metadata(label = "createAlias deleteAlias getAlias", description = "The function name of the alias", javaType = "String",
              required = true)
    String FUNCTION_ALIAS_NAME = "CamelAwsLambdaAliasFunctionName";
    @Metadata(label = "createAlias", description = "The function description to set in the alias", javaType = "String")
    String FUNCTION_ALIAS_DESCRIPTION = "CamelAwsLambdaAliasFunctionDescription";
}
