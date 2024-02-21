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

    @Metadata(description = "An image ID of the AWS marketplace", javaType = "String")
    String IMAGE_ID = "CamelAwsEC2ImageId";
    @Metadata(description = "The instance type we want to create and run",
              javaType = "software.amazon.awssdk.services.ec2.model.InstanceType")
    String INSTANCE_TYPE = "CamelAwsEC2InstanceType";
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsEC2Operation";
    @Metadata(description = "The minimum number of instances we want to run.", javaType = "Integer")
    String INSTANCE_MIN_COUNT = "CamelAwsEC2InstanceMinCount";
    @Metadata(description = "The maximum number of instances we want to run.", javaType = "Integer")
    String INSTANCE_MAX_COUNT = "CamelAwsEC2InstanceMaxCount";
    @Metadata(description = "Define if we want the running instances to be monitored", javaType = "Boolean")
    String INSTANCE_MONITORING = "CamelAwsEC2InstanceMonitoring";
    @Metadata(description = "The ID of the kernel.", javaType = "String")
    String INSTANCE_KERNEL_ID = "CamelAwsEC2InstanceKernelId";
    @Metadata(description = "Define if the creating instance is optimized for EBS I/O.", javaType = "Boolean")
    String INSTANCE_EBS_OPTIMIZED = "CamelAwsEC2InstanceEbsOptimized";
    @Metadata(description = "The security groups to associate to the instances", javaType = "Collection<String>")
    String INSTANCE_SECURITY_GROUPS = "CamelAwsEC2InstanceSecurityGroups";
    @Metadata(description = "A collection of instances IDS to execute start, stop, describe and\n" +
                            "terminate operations on.",
              javaType = "Collection<String>")
    String INSTANCES_IDS = "CamelAwsEC2InstancesIds";
    @Metadata(description = "The name of the key pair.", javaType = "String")
    String INSTANCES_KEY_PAIR = "CamelAwsEC2InstancesKeyPair";
    @Metadata(description = "Unique, case-sensitive identifier you provide to ensure the idempotency of the request.",
              javaType = "String")
    String INSTANCES_CLIENT_TOKEN = "CamelAwsEC2InstancesClientToken";
    @Metadata(description = "The placement for the instance.", javaType = "software.amazon.awssdk.services.ec2.model.Placement")
    String INSTANCES_PLACEMENT = "CamelAwsEC2InstancesPlacement";
    @Metadata(description = "A collection of tags to add or remove from EC2 resources", javaType = "Collection<Tag>")
    String INSTANCES_TAGS = "CamelAwsEC2InstancesTags";
    @Metadata(description = "The ID of the subnet to launch the instance into.", javaType = "String")
    String SUBNET_ID = "CamelAwsEC2SubnetId";
}
