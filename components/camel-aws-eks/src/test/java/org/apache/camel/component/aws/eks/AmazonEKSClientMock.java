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
package org.apache.camel.component.aws.eks;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.eks.AbstractAmazonEKS;
import com.amazonaws.services.eks.model.Cluster;
import com.amazonaws.services.eks.model.ClusterStatus;
import com.amazonaws.services.eks.model.CreateClusterRequest;
import com.amazonaws.services.eks.model.CreateClusterResult;
import com.amazonaws.services.eks.model.DeleteClusterRequest;
import com.amazonaws.services.eks.model.DeleteClusterResult;
import com.amazonaws.services.eks.model.DescribeClusterRequest;
import com.amazonaws.services.eks.model.DescribeClusterResult;
import com.amazonaws.services.eks.model.ListClustersRequest;
import com.amazonaws.services.eks.model.ListClustersResult;

public class AmazonEKSClientMock extends AbstractAmazonEKS {

    public AmazonEKSClientMock() {
    }
    
    @Override
    public CreateClusterResult createCluster(CreateClusterRequest request) {
        CreateClusterResult res = new CreateClusterResult();
        Cluster cluster = new Cluster();
        cluster.setName("Test");
        cluster.setStatus(ClusterStatus.ACTIVE.name());
        res.setCluster(cluster);
        return res;
    }

    @Override
    public DeleteClusterResult deleteCluster(DeleteClusterRequest request) {
        DeleteClusterResult res = new DeleteClusterResult();
        Cluster cluster = new Cluster();
        cluster.setName("Test");
        cluster.setStatus(ClusterStatus.DELETING.name());
        res.setCluster(cluster);
        return res;
    }

    @Override
    public DescribeClusterResult describeCluster(DescribeClusterRequest request) {
        DescribeClusterResult res = new DescribeClusterResult();
        Cluster cluster = new Cluster();
        cluster.setName("Test");
        cluster.setStatus(ClusterStatus.ACTIVE.name());
        res.setCluster(cluster);
        return res;        
    }

    @Override
    public ListClustersResult listClusters(ListClustersRequest request) {
        ListClustersResult res = new ListClustersResult();
        List<String> list = new ArrayList<>();
        list.add("Test");
        res.setClusters(list);
        return res;
    }
}
