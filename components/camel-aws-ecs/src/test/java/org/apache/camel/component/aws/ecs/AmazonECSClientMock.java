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
package org.apache.camel.component.aws.ecs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.amazonaws.services.ecs.AbstractAmazonECS;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.CreateClusterRequest;
import com.amazonaws.services.ecs.model.CreateClusterResult;
import com.amazonaws.services.ecs.model.DeleteClusterRequest;
import com.amazonaws.services.ecs.model.DeleteClusterResult;
import com.amazonaws.services.ecs.model.DescribeClustersRequest;
import com.amazonaws.services.ecs.model.DescribeClustersResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;

public class AmazonECSClientMock extends AbstractAmazonECS {

    public AmazonECSClientMock() {
    }
    
    @Override
    public CreateClusterResult createCluster(CreateClusterRequest request) {
        CreateClusterResult res = new CreateClusterResult();
        Cluster cluster = new Cluster();
        cluster.setClusterName("Test");
        res.setCluster(cluster);
        return res;
    }

    @Override
    public DeleteClusterResult deleteCluster(DeleteClusterRequest request) {
        DeleteClusterResult res = new DeleteClusterResult();
        Cluster cluster = new Cluster();
        cluster.setClusterName("Test");
        cluster.setStatus("INACTIVE");
        res.setCluster(cluster);
        return res;
    }

    @Override
    public DescribeClustersResult describeClusters(DescribeClustersRequest request) {
        DescribeClustersResult res = new DescribeClustersResult();
        Cluster cluster = new Cluster();
        cluster.setClusterName("Test");
        cluster.setStatus("ACTIVE");
        res.setClusters(Collections.singleton(cluster));
        return res;        
    }

    @Override
    public ListClustersResult listClusters(ListClustersRequest request) {
        ListClustersResult res = new ListClustersResult();
        List<String> list = new ArrayList<>();
        list.add("Test");
        res.setClusterArns(list);
        return res;
    }
}
