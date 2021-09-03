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
package org.apache.camel.component.huaweicloud.obs.models;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.util.ObjectHelper;

/**
 * Class containing Huawei Cloud OBS regions and endpoints
 */
public final class OBSRegion {
    private static final Map<String, String> REGIONS = createRegions();

    private OBSRegion() {
    }

    /**
     * Create map of all OBS regions and endpoints
     *
     * @return
     */
    private static Map<String, String> createRegions() {
        Map<String, String> map = new HashMap<>();
        map.put("af-south-1", "obs.af-south-1.myhuaweicloud.com");
        map.put("ap-southeast-2", "obs.ap-southeast-2.myhuaweicloud.com");
        map.put("ap-southeast-3", "obs.ap-southeast-3.myhuaweicloud.com");
        map.put("cn-east-3", "obs.cn-east-3.myhuaweicloud.com");
        map.put("cn-east-2", "obs.cn-east-2.myhuaweicloud.com");
        map.put("cn-north-1", "obs.cn-north-1.myhuaweicloud.com");
        map.put("cn-south-1", "obs.cn-south-1.myhuaweicloud.com");
        map.put("ap-southeast-1", "obs.ap-southeast-1.myhuaweicloud.com");
        map.put("sa-argentina-1", "obs.sa-argentina-1.myhuaweicloud.com");
        map.put("sa-peru-1", "obs.sa-peru-1.myhuaweicloud.com");
        map.put("na-mexico-1", "obs.na-mexico-1.myhuaweicloud.com");
        map.put("la-south-2", "obs.la-south-2.myhuaweicloud.com");
        map.put("sa-chile-1", "obs.sa-chile-1.myhuaweicloud.com");
        map.put("sa-brazil-1", "obs.sa-brazil-1.myhuaweicloud.com");
        return map;
    }

    /**
     * Determine endpoint based on regionId
     *
     * @param  regionId
     * @return
     */
    public static String valueOf(String regionId) {
        if (ObjectHelper.isEmpty(regionId)) {
            throw new IllegalArgumentException("Unexpected empty parameter: regionId.");
        } else {
            String endpoint = REGIONS.get(regionId.toLowerCase());
            if (ObjectHelper.isNotEmpty(endpoint)) {
                return endpoint;
            } else {
                throw new IllegalArgumentException("Unexpected regionId: " + regionId);
            }
        }
    }

    /**
     * Check if regionId is a valid region
     *
     * @param regionId
     */
    public static void checkValidRegion(String regionId) {
        if (ObjectHelper.isEmpty(regionId)) {
            throw new IllegalArgumentException("Unexpected empty parameter: regionId.");
        } else if (ObjectHelper.isEmpty(REGIONS.get(regionId.toLowerCase()))) {
            throw new IllegalArgumentException("Unexpected regionId: " + regionId);
        }
    }
}
