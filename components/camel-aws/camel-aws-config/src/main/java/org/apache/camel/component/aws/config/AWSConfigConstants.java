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
package org.apache.camel.component.aws.config;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Config module
 */
public interface AWSConfigConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsConfigOperation";
    @Metadata(description = "The Managed rule source identifier", javaType = "String")
    String RULE_SOURCE_IDENTIFIER = "CamelAwsConfigRuleSourceIdentifier";
    @Metadata(description = "The source object for the rule. The owner of the rule could be AWS, CUSTOM_LAMBDA or CUSTOM_POLICY",
              javaType = "String")
    String SOURCE = "CamelAwsConfigRuleSource";
    @Metadata(description = "The Managed rule name", javaType = "String")
    String RULE_NAME = "CamelAwsConfigRuleName";

    @Metadata(description = "The Conformance pack name", javaType = "String")
    String CONFORMACE_PACK_NAME = "CamelAwsConformancePackName";

    @Metadata(description = "The location of the file containing the template body in S3", javaType = "String")
    String CONFORMACE_PACK_S3_TEMPLATE_URI = "CamelAwsConfigConformacePackS3TemplateURI";

    @Metadata(description = "A string containing the full conformance pack template body", javaType = "String")
    String CONFORMACE_PACK_TEMPLATE_BODY = "CamelAwsConfigConformacePackTemplateBody";
}
