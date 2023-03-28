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
package org.apache.camel.component.aws2.eks;

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.eks.EksClient;
import software.amazon.awssdk.services.eks.EksServiceClientConfiguration;
import software.amazon.awssdk.services.eks.model.Cluster;
import software.amazon.awssdk.services.eks.model.ClusterStatus;
import software.amazon.awssdk.services.eks.model.CreateClusterRequest;
import software.amazon.awssdk.services.eks.model.CreateClusterResponse;
import software.amazon.awssdk.services.eks.model.DeleteClusterRequest;
import software.amazon.awssdk.services.eks.model.DeleteClusterResponse;
import software.amazon.awssdk.services.eks.model.DescribeClusterRequest;
import software.amazon.awssdk.services.eks.model.DescribeClusterResponse;
import software.amazon.awssdk.services.eks.model.ListClustersRequest;
import software.amazon.awssdk.services.eks.model.ListClustersResponse;

public class AmazonEKSClientMock implements EksClient {

    public AmazonEKSClientMock() {
    }

    @Override
    public CreateClusterResponse createCluster(CreateClusterRequest request) {
        CreateClusterResponse.Builder res = CreateClusterResponse.builder();
        Cluster cluster = Cluster.builder().name("Test").status(ClusterStatus.ACTIVE.name()).build();
        res.cluster(cluster);
        return res.build();
    }

    @Override
    public DeleteClusterResponse deleteCluster(DeleteClusterRequest request) {
        DeleteClusterResponse.Builder res = DeleteClusterResponse.builder();
        Cluster cluster = Cluster.builder().name("Test").status(ClusterStatus.DELETING.name()).build();
        res.cluster(cluster);
        return res.build();
    }

    @Override
    public DescribeClusterResponse describeCluster(DescribeClusterRequest request) {
        DescribeClusterResponse.Builder res = DescribeClusterResponse.builder();
        Cluster cluster = Cluster.builder().name("Test").status(ClusterStatus.ACTIVE.name()).build();
        res.cluster(cluster);
        return res.build();
    }

    @Override
    public ListClustersResponse listClusters(ListClustersRequest request) {
        ListClustersResponse.Builder res = ListClustersResponse.builder();
        List<String> list = new ArrayList<>();
        list.add("Test");
        res.clusters(list);
        return res.build();
    }

    @Override
    public EksServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public String serviceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }
}
