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
package org.apache.camel.component.huaweicloud.smn;

import com.huaweicloud.sdk.core.region.Region;
import com.huaweicloud.sdk.smn.v2.region.SmnRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * utility functions for the component
 */
public class SimpleNotificationUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleNotificationUtils.class.getName());

    /**
     * resolves endpoint url for the given region
     * 
     * @param  region
     * @return
     */
    public static String resolveSmnServiceEndpoint(String region) {
        if (region == null) {
            return null;
        }

        String result = null;

        try {
            String formattedEndpointKey = formatEndpointKey(region);
            result = ((Region) SmnRegion.class.getField(formattedEndpointKey)
                    .get(SmnRegion.class.getField(formattedEndpointKey)))
                            .getEndpoint();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOG.error("Couldn't resolve endpoint for region :  {}", region);
            result = null;
        }

        if (LOG.isDebugEnabled()) {
            LOG.info("Returning endpoint {} for region {}", result, region);
        }

        return result;
    }

    /**
     * formats the region id to SmnRegion class variable name
     * 
     * @param  region
     * @return
     */
    private static String formatEndpointKey(String region) {
        return region.toUpperCase().replace("-", "_");
    }
}
