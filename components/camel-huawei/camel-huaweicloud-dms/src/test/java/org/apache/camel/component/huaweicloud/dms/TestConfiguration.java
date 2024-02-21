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
package org.apache.camel.component.huaweicloud.dms;

import java.util.HashMap;
import java.util.Map;

public class TestConfiguration {
    private static Map<String, String> propertyMap;

    public TestConfiguration() {
        initPropertyMap();
    }

    private void initPropertyMap() {
        propertyMap = new HashMap<>();
        propertyMap.put("accessKey", "dummy_access_key");
        propertyMap.put("secretKey", "dummy_secret_key");
        propertyMap.put("region", "dummy_region");
        propertyMap.put("endpoint", "dummy_endpoint");
        propertyMap.put("projectId", "dummy_project_id");
        propertyMap.put("engine", "kafka");
        propertyMap.put("instanceId", "dummy_id");

        propertyMap.put("name", "dummy_name");
        propertyMap.put("description", "dummy_description");
        propertyMap.put("engineVersion", "dummy_engine_version");
        propertyMap.put("specification", "dummy_specification");
        propertyMap.put("accessUser", "dummy_access_user");
        propertyMap.put("password", "dummy_password");
        propertyMap.put("vpcId", "dummy_vpc_id");
        propertyMap.put("securityGroupId", "dummy_security_group_id");
        propertyMap.put("subnetId", "dummy_subnet_id");
        propertyMap.put("availableZone", "dummy_available_zone_id");
        propertyMap.put("productId", "dummy_product_id");
        propertyMap.put("kafkaManagerUser", "dummy_kafka_user");
        propertyMap.put("kafkaManagerPassword", "dummy_kafka_password");
        propertyMap.put("maintainBegin", "dummy_maintain_begin");
        propertyMap.put("maintainEnd", "dummy_maintain_end");
        propertyMap.put("publicBandwidth", "dummy_public_bandwidth");
        propertyMap.put("publicipId", "dummy_publicip_id");
        propertyMap.put("retentionPolicy", "dummy_retention_policy");
        propertyMap.put("storageSpecCode", "dummy_storage_spec_code");
        propertyMap.put("enterpriseProjectId", "dummy_enterprise_id");
    }

    public String getProperty(String key) {
        if (propertyMap == null) {
            initPropertyMap();
        }
        return propertyMap.get(key);
    }
}
