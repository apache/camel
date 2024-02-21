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
package org.apache.camel.component.huaweicloud.obs;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(TestConfiguration.class.getName());
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
        propertyMap.put("bucketName", "dummy_bucket_name");
        propertyMap.put("bucketLocation", "cn-north-1");
    }

    public String getProperty(String key) {
        if (propertyMap == null) {
            initPropertyMap();
        }
        return propertyMap.get(key);
    }
}
