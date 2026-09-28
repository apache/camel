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
package org.apache.camel.management.mbean;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.util.URISupport;

/**
 * The endpoint utilization statistics of an EIP (such as toD) as tabular data.
 */
final class EndpointUtilizationHelper {

    private EndpointUtilizationHelper() {
    }

    static TabularData toTabularData(EndpointUtilizationStatistics stats, boolean sanitize) throws OpenDataException {
        TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.endpointsUtilizationTabularType());
        if (stats != null) {
            // endpoints that only differ in a secret are the same url when sanitized, so their hits are merged
            Map<String, Long> hitsPerUrl = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : stats.getStatistics().entrySet()) {
                String url = entry.getKey();
                if (sanitize) {
                    url = URISupport.sanitizeUri(url);
                }
                long hits = entry.getValue() != null ? entry.getValue() : 0L;
                hitsPerUrl.merge(url, hits, Long::sum);
            }
            CompositeType ct = CamelOpenMBeanTypes.endpointsUtilizationCompositeType();
            for (Map.Entry<String, Long> entry : hitsPerUrl.entrySet()) {
                CompositeData data = new CompositeDataSupport(
                        ct, new String[] { "url", "hits" }, new Object[] { entry.getKey(), entry.getValue() });
                answer.put(data);
            }
        }
        return answer;
    }
}
