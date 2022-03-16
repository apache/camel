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

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS EKS module SDK v2
 */
public interface EKS2Constants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsEKSOperation";
    @Metadata(description = "The limit number of results while listing clusters", javaType = "Integer")
    String MAX_RESULTS = "CamelAwsEKSMaxResults";
    @Metadata(description = "A key description to use while performing a createKey operation", javaType = "String")
    String DESCRIPTION = "CamelAwsEKSDescription";
    @Metadata(description = "The cluster name", javaType = "String")
    String CLUSTER_NAME = "CamelAwsEKSClusterName";
    @Metadata(description = "The role ARN to use while creating the cluster", javaType = "String")
    String ROLE_ARN = "CamelAwsEKSRoleARN";
    @Metadata(description = "The VPC config for the creations of an EKS cluster",
              javaType = "software.amazon.awssdk.services.eks.model.VpcConfigRequest")
    String VPC_CONFIG = "CamelAwsEKSVPCConfig";
}
