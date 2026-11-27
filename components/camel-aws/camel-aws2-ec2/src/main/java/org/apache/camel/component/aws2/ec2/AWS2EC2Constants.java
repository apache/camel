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
package org.apache.camel.component.aws2.ec2;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS EC2 module SDK v2
 */
public interface AWS2EC2Constants {

    @Metadata(description = "The ID of the Amazon Machine Image (AMI) to launch, for createAndRunInstances, which requires"
                            + " it.",
              javaType = "String")
    String IMAGE_ID = "CamelAwsEC2ImageId";
    @Metadata(description = "The instance type of the instances to launch, for createAndRunInstances, which requires it. It"
                            + " is an InstanceType, its AWS value (such as t2.micro), or the name of the constant (such as"
                            + " T2_MICRO).",
              javaType = "software.amazon.awssdk.services.ec2.model.InstanceType")
    String INSTANCE_TYPE = "CamelAwsEC2InstanceType";
    @Metadata(description = "The operation that the producer performs. This header replaces the operation option.",
              javaType = "String")
    String OPERATION = "CamelAwsEC2Operation";
    @Metadata(description = "The minimum number of instances to launch, for createAndRunInstances, which requires it. If EC2"
                            + " cannot launch this many instances, it launches none.",
              javaType = "Integer")
    String INSTANCE_MIN_COUNT = "CamelAwsEC2InstanceMinCount";
    @Metadata(description = "The maximum number of instances to launch, for createAndRunInstances, which requires it. If EC2"
                            + " cannot launch this many instances, it launches as many as it can above the minimum.",
              javaType = "Integer")
    String INSTANCE_MAX_COUNT = "CamelAwsEC2InstanceMaxCount";
    @Metadata(description = "Whether detailed monitoring is enabled for the instances that createAndRunInstances launches."
                            + " If not set, EC2 enables basic monitoring only.",
              javaType = "Boolean")
    String INSTANCE_MONITORING = "CamelAwsEC2InstanceMonitoring";
    @Metadata(description = "The ID of the kernel of the instances that createAndRunInstances launches.", javaType = "String")
    String INSTANCE_KERNEL_ID = "CamelAwsEC2InstanceKernelId";
    @Metadata(description = "Whether the instances that createAndRunInstances launches are optimized for Amazon EBS I/O. Not"
                            + " every instance type supports it, and it can cost extra.",
              javaType = "Boolean")
    String INSTANCE_EBS_OPTIMIZED = "CamelAwsEC2InstanceEbsOptimized";
    @Metadata(description = "The names of the security groups of the instances that createAndRunInstances launches, as a"
                            + " Collection of String. EC2 accepts group names only in the default VPC; for another VPC, use"
                            + " pojoRequest with the security group IDs in the RunInstancesRequest. If not set, EC2 uses the"
                            + " default security group.",
              javaType = "Collection<String>")
    String INSTANCE_SECURITY_GROUPS = "CamelAwsEC2InstanceSecurityGroups";
    @Metadata(description = "The IDs of the instances, as a Collection of String or as a String of IDs separated by commas."
                            + " startInstances, stopInstances, terminateInstances, rebootInstances, monitorInstances and"
                            + " unmonitorInstances require it, and describeInstances and describeInstancesStatus filter by"
                            + " it when it is set. createTags and deleteTags require it"
                            + " too, as the IDs of the resources to tag, which can be any EC2 resources.",
              javaType = "Collection<String>")
    String INSTANCES_IDS = "CamelAwsEC2InstancesIds";
    @Metadata(description = "The name of the key pair of the instances that createAndRunInstances launches.",
              javaType = "String")
    String INSTANCES_KEY_PAIR = "CamelAwsEC2InstancesKeyPair";
    @Metadata(description = "A unique, case-sensitive token that makes createAndRunInstances idempotent: EC2 does not launch"
                            + " the instances again for a retry with the same token.",
              javaType = "String")
    String INSTANCES_CLIENT_TOKEN = "CamelAwsEC2InstancesClientToken";
    @Metadata(description = "The placement of the instances that createAndRunInstances launches, such as the Availability"
                            + " Zone.",
              javaType = "software.amazon.awssdk.services.ec2.model.Placement")
    String INSTANCES_PLACEMENT = "CamelAwsEC2InstancesPlacement";
    @Metadata(description = "The tags, as a Collection of Tag, that createTags adds to the resources in"
                            + " CamelAwsEC2InstancesIds, or that deleteTags removes from them. Both operations require it.",
              javaType = "Collection<software.amazon.awssdk.services.ec2.model.Tag>")
    String INSTANCES_TAGS = "CamelAwsEC2InstancesTags";
    @Metadata(description = "The ID of the subnet to launch the instances into, for createAndRunInstances.",
              javaType = "String")
    String SUBNET_ID = "CamelAwsEC2SubnetId";

    // Pagination constants
    @Metadata(label = "describeInstances describeInstancesStatus",
              description = "The pagination token of describeInstances and describeInstancesStatus. The producer reads it as"
                            + " the token of the page to return, and sets it on the result to the token of the next page,"
                            + " which has no value on the last page.",
              javaType = "String")
    String NEXT_TOKEN = "CamelAwsEC2NextToken";
    @Metadata(label = "describeInstances describeInstancesStatus",
              description = "The maximum number of results in one page of describeInstances and describeInstancesStatus,"
                            + " from 5 to 1000. EC2 does not accept it together with the IDs of CamelAwsEC2InstancesIds.",
              javaType = "Integer")
    String MAX_RESULTS = "CamelAwsEC2MaxResults";
    @Metadata(label = "describeInstances describeInstancesStatus",
              description = "Whether describeInstances or describeInstancesStatus has more results, which is when the result"
                            + " has a token of the next page in CamelAwsEC2NextToken. The producer sets it.",
              javaType = "Boolean")
    String IS_TRUNCATED = "CamelAwsEC2IsTruncated";
}
