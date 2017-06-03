/**
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
package org.apache.camel.component.aws.ec2;

/**
 * Constants used in Camel AWS EC2 module
 * 
 */
public interface EC2Constants {

    String IMAGE_ID                 = "CamelAwsEC2ImageId";
    String INSTANCE_TYPE            = "CamelAwsEC2InstanceType";
    String OPERATION                = "CamelAwsEC2Operation";
    String INSTANCE_MIN_COUNT       = "CamelAwsEC2InstanceMinCount";
    String INSTANCE_MAX_COUNT       = "CamelAwsEC2InstanceMaxCount";
    String INSTANCE_MONITORING      = "CamelAwsEC2InstanceMonitoring";
    String INSTANCE_KERNEL_ID       = "CamelAwsEC2InstanceKernelId";
    String INSTANCE_EBS_OPTIMIZED   = "CamelAwsEC2InstanceEbsOptimized";
    String INSTANCE_SECURITY_GROUPS = "CamelAwsEC2InstanceSecurityGroups";
    String INSTANCES_IDS            = "CamelAwsEC2InstancesIds";
    String INSTANCES_KEY_PAIR       = "CamelAwsEC2InstancesKeyPair";
    String INSTANCES_CLIENT_TOKEN   = "CamelAwsEC2InstancesClientToken";
    String INSTANCES_PLACEMENT      = "CamelAwsEC2InstancesPlacement";
    String INSTANCES_TAGS           = "CamelAwsEC2InstancesTags";
    String SUBNET_ID                = "CamelAwsEC2SubnetId";
}
