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
package org.apache.camel.component.aws2.msk;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS MSK module SDK v2
 */
public interface MSK2Constants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsMSKOperation";
    @Metadata(description = "The cluster name filter for list operation", javaType = "String")
    String CLUSTERS_FILTER = "CamelAwsMSKClusterFilter";
    @Metadata(description = "The cluster name for list and create operation", javaType = "String")
    String CLUSTER_NAME = "CamelAwsMSKClusterName";
    @Metadata(description = "The cluster arn for delete operation", javaType = "String")
    String CLUSTER_ARN = "CamelAwsMSKClusterArn";
    @Metadata(description = "The Kafka for the cluster during create operation", javaType = "String")
    String CLUSTER_KAFKA_VERSION = "CamelAwsMSKClusterKafkaVersion";
    @Metadata(description = "The number of nodes for the cluster during create operation", javaType = "Integer")
    String BROKER_NODES_NUMBER = "CamelAwsMSKBrokerNodesNumber";
    @Metadata(description = "The Broker nodes group info to provide during the create operation",
              javaType = "software.amazon.awssdk.services.kafka.model.BrokerNodeGroupInfo")
    String BROKER_NODES_GROUP_INFO = "CamelAwsMSKBrokerNodesGroupInfo";
}
