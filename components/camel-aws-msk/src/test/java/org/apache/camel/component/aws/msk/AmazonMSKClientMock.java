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
package org.apache.camel.component.aws.msk;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.kafka.AbstractAWSKafka;
import com.amazonaws.services.kafka.model.ClusterInfo;
import com.amazonaws.services.kafka.model.ClusterState;
import com.amazonaws.services.kafka.model.CreateClusterRequest;
import com.amazonaws.services.kafka.model.CreateClusterResult;
import com.amazonaws.services.kafka.model.DeleteClusterRequest;
import com.amazonaws.services.kafka.model.DeleteClusterResult;
import com.amazonaws.services.kafka.model.DescribeClusterRequest;
import com.amazonaws.services.kafka.model.DescribeClusterResult;
import com.amazonaws.services.kafka.model.ListClustersRequest;
import com.amazonaws.services.kafka.model.ListClustersResult;

public class AmazonMSKClientMock extends AbstractAWSKafka {

    public AmazonMSKClientMock() {
    }

    @Override
    public ListClustersResult listClusters(ListClustersRequest request) {
        ListClustersResult result = new ListClustersResult();
        List<ClusterInfo> info = new ArrayList<>();
        ClusterInfo info1 = new ClusterInfo();
        info1.setClusterName("test-kafka");
        info.add(info1);
        result.setClusterInfoList(info);
        return result;
    }

    @Override
    public CreateClusterResult createCluster(CreateClusterRequest request) {
        CreateClusterResult result = new CreateClusterResult();
        result.setClusterName(request.getClusterName());
        result.setState(ClusterState.CREATING.name());
        return result;
    }

    @Override
    public DeleteClusterResult deleteCluster(DeleteClusterRequest request) {
        DeleteClusterResult res = new DeleteClusterResult();
        res.setClusterArn(request.getClusterArn());
        res.setState(ClusterState.DELETING.name());
        return res;
    }
    
    @Override
    public DescribeClusterResult describeCluster(DescribeClusterRequest request) {
        DescribeClusterResult res = new DescribeClusterResult();
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterArn("test-kafka");
        clusterInfo.setState(ClusterState.ACTIVE.name());
        res.setClusterInfo(clusterInfo);
        return res;
    }
}
