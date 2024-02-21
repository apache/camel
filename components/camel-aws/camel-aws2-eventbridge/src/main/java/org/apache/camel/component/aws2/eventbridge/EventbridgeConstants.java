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
package org.apache.camel.component.aws2.eventbridge;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS2 Eventbridge module
 */
public interface EventbridgeConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsEventbridgeOperation";
    @Metadata(description = "The name of the rule.", javaType = "String")
    String RULE_NAME = "CamelAwsEventbridgeRuleName";
    @Metadata(description = "The prefix matching the rule name.", javaType = "String")
    String RULE_NAME_PREFIX = "CamelAwsEventbridgeRuleNamePrefix";
    @Metadata(description = "The event pattern.", javaType = "String")
    String EVENT_PATTERN = "CamelAwsEventbridgeEventPattern";
    @Metadata(description = "The targets to update or add to the rule.", javaType = "Collection<Target>")
    String TARGETS = "CamelAwsEventbridgeTargets";
    @Metadata(description = "The IDs of the targets to remove from the rule.", javaType = "Collection<String>")
    String TARGETS_IDS = "CamelAwsEventbridgeTargetsIds";
    @Metadata(description = "The Amazon Resource Name (ARN) of the target resource.", javaType = "String")
    String TARGET_ARN = "CamelAwsEventbridgeTargetArn";
    @Metadata(description = "Comma separated list of Amazon Resource Names (ARN) of the resources related to Event",
              javaType = "String")
    String EVENT_RESOURCES_ARN = "CamelAwsEventbridgeResourcesArn";
    @Metadata(description = "The source related to Event", javaType = "String")
    String EVENT_SOURCE = "CamelAwsEventbridgeSource";
    @Metadata(description = "The detail type related to Event", javaType = "String")
    String EVENT_DETAIL_TYPE = "CamelAwsEventbridgeDetailType";
}
