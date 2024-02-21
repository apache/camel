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
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsMQOperation";
    @Metadata(description = "The number of results that must be retrieved from listBrokers operation", javaType = "Integer")
    String MAX_RESULTS = "CamelAwsMQMaxResults";
    @Metadata(description = "The broker name", javaType = "String")
    String BROKER_NAME = "CamelAwsMQBrokerName";
    @Metadata(description = "The Broker Engine for MQ.", javaType = "String")
    String BROKER_ENGINE = "CamelAwsMQBrokerEngine";
    @Metadata(description = "The Broker Engine Version for MQ. Currently you can choose between 5.15.6 and 5.15.0 of ACTIVEMQ",
              javaType = "String")
    String BROKER_ENGINE_VERSION = "CamelAwsMQBrokerEngineVersion";
    @Metadata(description = "The broker id", javaType = "String")
    String BROKER_ID = "CamelAwsMQBrokerID";
    @Metadata(description = "A list of information about the configuration.",
              javaType = "software.amazon.awssdk.services.mq.model.ConfigurationId")
    String CONFIGURATION_ID = "CamelAwsMQConfigurationID";
    @Metadata(description = "The deployment mode for the broker in the createBroker operation", javaType = "String")
    String BROKER_DEPLOYMENT_MODE = "CamelAwsMQBrokerDeploymentMode";
    @Metadata(description = "The instance type for the MQ machine in the createBroker operation", javaType = "String")
    String BROKER_INSTANCE_TYPE = "CamelAwsMQBrokerInstanceType";
    @Metadata(description = "The list of users for MQ", javaType = "List<User>")
    String BROKER_USERS = "CamelAwsMQBrokerUsers";
    @Metadata(description = "If the MQ instance must be publicly available or not.", javaType = "Boolean",
              defaultValue = "false")
    String BROKER_PUBLICLY_ACCESSIBLE = "CamelAwsMQBrokerPubliclyAccessible";
}
