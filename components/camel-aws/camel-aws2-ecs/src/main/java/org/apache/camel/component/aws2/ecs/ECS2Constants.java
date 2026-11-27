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
package org.apache.camel.component.aws2.ecs;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS2 ECS module
 */
public interface ECS2Constants {
    @Metadata(label = "producer", description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsECSOperation";
    @Metadata(label = "producer", description = "The limit number of results while listing clusters", javaType = "Integer")
    String MAX_RESULTS = "CamelAwsECSMaxResults";
    @Metadata(label = "producer",
              description = "The cluster name, for createCluster, describeCluster and deleteCluster", javaType = "String")
    String CLUSTER_NAME = "CamelAwsECSClusterName";

    // Pagination constants
    @Metadata(label = "producer",
              description = "The token for the next set of results of listClusters.", javaType = "String")
    String NEXT_TOKEN = "CamelAwsECSNextToken";
    @Metadata(label = "producer",
              description = "Whether the response of listClusters has more results (is truncated).", javaType = "Boolean")
    String IS_TRUNCATED = "CamelAwsECSIsTruncated";

    // Response metadata
    @Metadata(label = "producer",
              description = "The ARN of the cluster, set by createCluster, describeCluster and deleteCluster.",
              javaType = "String")
    String CLUSTER_ARN = "CamelAwsECSClusterArn";
}
