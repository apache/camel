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
package org.apache.camel.component.huaweicloud.dms.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DMSPropertiesTest {
    @Test
    public void test() {
        assertEquals("CamelHwCloudDmsOperation", DMSProperties.OPERATION);
        assertEquals("CamelHwCloudDmsEngine", DMSProperties.ENGINE);
        assertEquals("CamelHwCloudDmsInstanceId", DMSProperties.INSTANCE_ID);
        assertEquals("CamelHwCloudDmsName", DMSProperties.NAME);
        assertEquals("CamelHwCloudDmsEngineVersion", DMSProperties.ENGINE_VERSION);
        assertEquals("CamelHwCloudDmsSpecification", DMSProperties.SPECIFICATION);
        assertEquals("CamelHwCloudDmsStorageSpace", DMSProperties.STORAGE_SPACE);
        assertEquals("CamelHwCloudDmsPartitionNum", DMSProperties.PARTITION_NUM);
        assertEquals("CamelHwCloudDmsAccessUser", DMSProperties.ACCESS_USER);
        assertEquals("CamelHwCloudDmsPassword", DMSProperties.PASSWORD);
        assertEquals("CamelHwCloudDmsVpcId", DMSProperties.VPC_ID);
        assertEquals("CamelHwCloudDmsSecurityGroupId", DMSProperties.SECURITY_GROUP_ID);
        assertEquals("CamelHwCloudDmsSubnetId", DMSProperties.SUBNET_ID);
        assertEquals("CamelHwCloudDmsAvailableZones", DMSProperties.AVAILABLE_ZONES);
        assertEquals("CamelHwCloudDmsProductId", DMSProperties.PRODUCT_ID);
        assertEquals("CamelHwCloudDmsKafkaManagerUser", DMSProperties.KAFKA_MANAGER_USER);
        assertEquals("CamelHwCloudDmsKafkaManagerPassword", DMSProperties.KAFKA_MANAGER_PASSWORD);
        assertEquals("CamelHwCloudDmsStorageSpecCode", DMSProperties.STORAGE_SPEC_CODE);

        assertEquals("CamelHwCloudDmsInstanceDeleted", DMSProperties.INSTANCE_DELETED);
        assertEquals("CamelHwCloudDmsInstanceUpdated", DMSProperties.INSTANCE_UPDATED);
    }
}
