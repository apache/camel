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
package org.apache.camel.component.quartz2;

import org.apache.camel.CamelContext;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QuartzHelper {

    public static final Logger LOG = LoggerFactory.getLogger(QuartzEndpoint.class);

    private QuartzHelper() {
    }

    public static String getQuartzContextName(CamelContext camelContext) {
        // favour using the actual management name which was registered in JMX (if JMX is enabled)
        if (camelContext.getManagementName() != null) {
            return camelContext.getManagementName();
        } else {
            return camelContext.getManagementNameStrategy().getName();
        }
    }

    public static void updateJobDataMap(CamelContext camelContext, JobDetail jobDetail, String endpointUri) {
        // Store this camelContext name into the job data
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        String camelContextName = QuartzHelper.getQuartzContextName(camelContext);
        LOG.debug("Adding camelContextName={}, endpointUri={} into job data map.", camelContextName, endpointUri);
        jobDataMap.put(QuartzConstants.QUARTZ_CAMEL_CONTEXT_NAME, camelContextName);
        jobDataMap.put(QuartzConstants.QUARTZ_ENDPOINT_URI, endpointUri);
    }

}
