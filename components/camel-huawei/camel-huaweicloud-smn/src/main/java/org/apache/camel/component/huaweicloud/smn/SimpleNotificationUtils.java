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

import com.huaweicloud.sdk.smn.v2.region.SmnRegion;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * utility functions for the component
 */
public final class SimpleNotificationUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleNotificationUtils.class.getName());

    private SimpleNotificationUtils() {
    }

    /**
     * resolves endpoint url for the given region
     *
     * @param  region
     * @return
     */
    public static String resolveSmnServiceEndpoint(String region) {
        if (region == null || StringUtils.isEmpty(region)) {
            return null;
        }

        String result = SmnRegion.valueOf(region).getEndpoint();

        if (LOG.isDebugEnabled()) {
            LOG.debug("endpoint resolved as {} for region {}", result, region);
        }

        if (ObjectHelper.isEmpty(result)) {
            LOG.error("Couldn't resolve endpoint for region :  {}", region);
            result = null;
        }

        if (LOG.isDebugEnabled()) {
            LOG.info("Returning endpoint {} for region {}", result, region);
        }
        return result;
    }
}
