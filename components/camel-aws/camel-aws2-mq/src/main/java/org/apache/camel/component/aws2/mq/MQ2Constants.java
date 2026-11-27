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
package org.apache.camel.component.aws2.mq;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS MQ module SDK v2
 */
public interface MQ2Constants {
    @Metadata(description = "The operation that the producer performs. It replaces the operation option.", javaType = "String")
    String OPERATION = "CamelAwsMQOperation";
    @Metadata(description = "The maximum number of brokers in one page of listBrokers. Amazon MQ accepts a value from 5 to 100, and returns 20 brokers per page when it is not set.",
              javaType = "Integer")
    String MAX_RESULTS = "CamelAwsMQMaxResults";
    @Metadata(description = "The name of the broker that createBroker creates, which createBroker requires. Amazon MQ requires a name that is unique in the AWS account, 1 to 50 characters long, with only letters, digits, dashes and underscores.",
              javaType = "String")
    String BROKER_NAME = "CamelAwsMQBrokerName";
    @Metadata(description = "The engine of the broker that createBroker creates: ACTIVEMQ or RABBITMQ, in upper case. createBroker requires it.",
              javaType = "String")
    String BROKER_ENGINE = "CamelAwsMQBrokerEngine";
    @Metadata(description = "The version of the engine of the broker that createBroker creates, which must be a version that Amazon MQ supports for the engine of the CamelAwsMQBrokerEngine header. createBroker requires it.",
              javaType = "String")
    String BROKER_ENGINE_VERSION = "CamelAwsMQBrokerEngineVersion";
    @Metadata(description = "The ID of the broker that deleteBroker, rebootBroker, updateBroker and describeBroker act on, which they require. createBroker sets it to the ID of the new broker.",
              javaType = "String")
    String BROKER_ID = "CamelAwsMQBrokerID";
    @Metadata(description = "The configuration that updateBroker applies to the broker: a ConfigurationId of the AWS SDK, with the ID and optionally the revision of the configuration, or the ID of the configuration as a String. updateBroker requires it.",
              javaType = "software.amazon.awssdk.services.mq.model.ConfigurationId")
    String CONFIGURATION_ID = "CamelAwsMQConfigurationID";
    @Metadata(description = "The deployment mode of the broker that createBroker creates: SINGLE_INSTANCE, ACTIVE_STANDBY_MULTI_AZ for ActiveMQ, or CLUSTER_MULTI_AZ for RabbitMQ. createBroker requires it.",
              javaType = "String")
    String BROKER_DEPLOYMENT_MODE = "CamelAwsMQBrokerDeploymentMode";
    @Metadata(description = "The instance type of the broker that createBroker creates, such as mq.t3.micro or mq.m5.large. createBroker requires it.",
              javaType = "String")
    String BROKER_INSTANCE_TYPE = "CamelAwsMQBrokerInstanceType";
    @Metadata(description = "The users of the broker that createBroker creates, as a List of User of the AWS SDK, each with a username and a password. createBroker requires at least one user, and a RabbitMQ broker accepts exactly one administrative user.",
              javaType = "List<software.amazon.awssdk.services.mq.model.User>")
    String BROKER_USERS = "CamelAwsMQBrokerUsers";
    @Metadata(description = "Whether applications outside the VPC of the broker that createBroker creates can connect to it. When not set, createBroker creates a broker that is not publicly accessible.",
              javaType = "Boolean",
              defaultValue = "false")
    String BROKER_PUBLICLY_ACCESSIBLE = "CamelAwsMQBrokerPubliclyAccessible";

    // Pagination constants
    @Metadata(label = "listBrokers",
              description = "The token of a page of listBrokers. listBrokers reads it to return that page instead of the first one, and sets it to the token of the next page, which has no value on the last page.",
              javaType = "String")
    String NEXT_TOKEN = "CamelAwsMQNextToken";
    @Metadata(label = "listBrokers",
              description = "Whether listBrokers has more brokers after the returned page, that is whether the CamelAwsMQNextToken header has a value.",
              javaType = "Boolean")
    String IS_TRUNCATED = "CamelAwsMQIsTruncated";

    // Response metadata
    @Metadata(label = "createBroker describeBroker",
              description = "The Amazon Resource Name (ARN) of the broker that createBroker created or describeBroker described.",
              javaType = "String")
    String BROKER_ARN = "CamelAwsMQBrokerArn";
    @Metadata(label = "describeBroker",
              description = "The state of the broker that describeBroker described, such as RUNNING, CREATION_IN_PROGRESS, REBOOT_IN_PROGRESS or CRITICAL_ACTION_REQUIRED.",
              javaType = "String")
    String BROKER_STATE = "CamelAwsMQBrokerState";
}
