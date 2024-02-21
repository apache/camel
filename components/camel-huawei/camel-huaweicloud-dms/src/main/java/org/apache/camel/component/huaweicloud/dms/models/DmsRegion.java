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
package org.apache.camel.component.huaweicloud.dms.models;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.huaweicloud.sdk.core.region.Region;
import com.huaweicloud.sdk.core.utils.StringUtils;

/**
 * Class containing Huawei Cloud OBS regions and endpoints
 */
public final class DmsRegion {
    public static final Region AF_SOUTH_1 = new Region("af-south-1", "https://dms.af-south-1.myhuaweicloud.com");
    public static final Region CN_NORTH_4 = new Region("cn-north-4", "https://dms.cn-north-4.myhuaweicloud.com");
    public static final Region CN_NORTH_1 = new Region("cn-north-1", "https://dms.cn-north-1.myhuaweicloud.com");
    public static final Region CN_EAST_2 = new Region("cn-east-2", "https://dms.cn-east-2.myhuaweicloud.com");
    public static final Region CN_EAST_3 = new Region("cn-east-3", "https://dms.cn-east-3.myhuaweicloud.com");
    public static final Region CN_SOUTH_1 = new Region("cn-south-1", "https://dms.cn-south-1.myhuaweicloud.com");
    public static final Region CN_SOUTHWEST_2 = new Region("cn-southwest-2", "https://dms.cn-southwest-2.myhuaweicloud.com");
    public static final Region AP_SOUTHEAST_2 = new Region("ap-southeast-2", "https://dms.ap-southeast-2.myhuaweicloud.com");
    public static final Region AP_SOUTHEAST_1 = new Region("ap-southeast-1", "https://dms.ap-southeast-1.myhuaweicloud.com");
    public static final Region AP_SOUTHEAST_3 = new Region("ap-southeast-3", "https://dms.ap-southeast-3.myhuaweicloud.com");
    private static final Map<String, Region> STATIC_FIELDS = createStaticFields();

    private DmsRegion() {
    }

    private static Map<String, Region> createStaticFields() {
        Map<String, Region> map = new HashMap<>();
        map.put("af-south-1", AF_SOUTH_1);
        map.put("cn-north-4", CN_NORTH_4);
        map.put("cn-north-1", CN_NORTH_1);
        map.put("cn-east-2", CN_EAST_2);
        map.put("cn-east-3", CN_EAST_3);
        map.put("cn-south-1", CN_SOUTH_1);
        map.put("cn-southwest-2", CN_SOUTHWEST_2);
        map.put("ap-southeast-2", AP_SOUTHEAST_2);
        map.put("ap-southeast-1", AP_SOUTHEAST_1);
        map.put("ap-southeast-3", AP_SOUTHEAST_3);
        return Collections.unmodifiableMap(map);
    }

    public static Region valueOf(String regionId) {
        if (StringUtils.isEmpty(regionId)) {
            throw new IllegalArgumentException("Unexpected empty parameter: regionId.");
        } else {
            Region result = STATIC_FIELDS.get(regionId);
            if (Objects.nonNull(result)) {
                return result;
            } else {
                throw new IllegalArgumentException("Unexpected regionId: " + regionId);
            }
        }
    }
}
