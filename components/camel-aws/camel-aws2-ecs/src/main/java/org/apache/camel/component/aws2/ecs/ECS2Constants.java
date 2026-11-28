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
    @Metadata(label = "producer", description = "The operation that the producer performs. It replaces the operation option.",
              javaType = "String")
    String OPERATION = "CamelAwsECSOperation";
    @Metadata(label = "producer",
              description = "The maximum number of clusters that listClusters returns in one page, from 1 to 100. If not"
                            + " set, Amazon ECS returns up to 100.",
              javaType = "Integer")
    String MAX_RESULTS = "CamelAwsECSMaxResults";
    @Metadata(label = "producer",
              description = "The short name or the full ARN of the cluster. createCluster takes the name of the new cluster"
                            + " and deleteCluster requires it. If not set, Amazon ECS uses the cluster named default for"
                            + " createCluster and describeCluster.",
              javaType = "String")
    String CLUSTER_NAME = "CamelAwsECSClusterName";

    // Pagination constants
    @Metadata(label = "producer",
              description = "The token of the next page of listClusters. The producer reads it to request that page, and"
                            + " sets it from the response, or to null on the last page.",
              javaType = "String")
    String NEXT_TOKEN = "CamelAwsECSNextToken";
    @Metadata(label = "producer",
              description = "Whether more clusters follow the ones that listClusters returned, that is whether the response"
                            + " has a next token. The producer sets it.",
              javaType = "Boolean")
    String IS_TRUNCATED = "CamelAwsECSIsTruncated";

    // Response metadata
    @Metadata(label = "producer",
              description = "The ARN of the cluster that the operation created, described or deleted. The producer sets it.",
              javaType = "String")
    String CLUSTER_ARN = "CamelAwsECSClusterArn";
}
