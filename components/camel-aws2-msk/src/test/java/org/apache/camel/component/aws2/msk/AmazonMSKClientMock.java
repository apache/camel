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

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.kafka.KafkaClient;
import software.amazon.awssdk.services.kafka.model.ClusterInfo;
import software.amazon.awssdk.services.kafka.model.ClusterState;
import software.amazon.awssdk.services.kafka.model.CreateClusterRequest;
import software.amazon.awssdk.services.kafka.model.CreateClusterResponse;
import software.amazon.awssdk.services.kafka.model.DeleteClusterRequest;
import software.amazon.awssdk.services.kafka.model.DeleteClusterResponse;
import software.amazon.awssdk.services.kafka.model.DescribeClusterRequest;
import software.amazon.awssdk.services.kafka.model.DescribeClusterResponse;
import software.amazon.awssdk.services.kafka.model.ListClustersRequest;
import software.amazon.awssdk.services.kafka.model.ListClustersResponse;

public class AmazonMSKClientMock implements KafkaClient {

    public AmazonMSKClientMock() {
    }

    @Override
    public ListClustersResponse listClusters(ListClustersRequest request) {
        ListClustersResponse.Builder result = ListClustersResponse.builder();
        List<ClusterInfo> info = new ArrayList<>();
        ClusterInfo.Builder info1 = ClusterInfo.builder();
        info1.clusterName("test-kafka");
        info.add(info1.build());
        result.clusterInfoList(info);
        return result.build();
    }

    @Override
    public CreateClusterResponse createCluster(CreateClusterRequest request) {
        CreateClusterResponse.Builder builder = CreateClusterResponse.builder();
        builder.clusterName(request.clusterName());
        builder.state(ClusterState.CREATING.name());
        return builder.build();
    }

    @Override
    public DeleteClusterResponse deleteCluster(DeleteClusterRequest request) {
        DeleteClusterResponse.Builder res = DeleteClusterResponse.builder();
        res.clusterArn(request.clusterArn());
        res.state(ClusterState.DELETING.name());
        return res.build();
    }

    @Override
    public DescribeClusterResponse describeCluster(DescribeClusterRequest request) {
        DescribeClusterResponse.Builder res = DescribeClusterResponse.builder();
        ClusterInfo.Builder clusterInfo = ClusterInfo.builder();
        clusterInfo.clusterArn("test-kafka");
        clusterInfo.state(ClusterState.ACTIVE.name());
        res.clusterInfo(clusterInfo.build());
        return res.build();
    }

    @Override
    public String serviceName() {
        return null;
    }

    @Override
    public void close() {
    }
}
