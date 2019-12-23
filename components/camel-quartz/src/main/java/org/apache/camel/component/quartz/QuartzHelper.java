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
package org.apache.camel.component.quartz;

import org.apache.camel.CamelContext;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QuartzHelper {

    public static final Logger LOG = LoggerFactory.getLogger(QuartzHelper.class);

    private QuartzHelper() {
        // prevent instantiation
    }

    public static String getQuartzContextName(CamelContext camelContext) {
        // favour using the actual management name which was registered in JMX (if JMX is enabled)
        if (camelContext.getManagementName() != null) {
            return camelContext.getManagementName();
        } else {
            // fallback as name
            return camelContext.getName();
        }
    }

    /**
     * Adds the current CamelContext name and endpoint URI to the Job's jobData
     * map.
     *
     * @param camelContext The currently active camelContext
     * @param jobDetail The job for which the jobData map shall be updated
     * @param endpointUri URI of the endpoint name, if any. May be {@code null}
     */
    public static void updateJobDataMap(CamelContext camelContext, JobDetail jobDetail, String endpointUri) {
        updateJobDataMap(camelContext, jobDetail, endpointUri, false);
    }

    /**
     * Adds the current CamelContext name and endpoint URI to the Job's jobData
     * map.
     *
     * @param camelContext The currently active camelContext
     * @param jobDetail The job for which the jobData map shall be updated
     * @param endpointUri URI of the endpoint name, if any. May be {@code null}
     * @param usingFixedCamelContextName If it is true, jobDataMap uses the CamelContext name;
     *  if it is false, jobDataMap uses the CamelContext management name which could be changed during the deploy time
     */
    public static void updateJobDataMap(CamelContext camelContext, JobDetail jobDetail, String endpointUri, boolean usingFixedCamelContextName) {
        // Store this camelContext name into the job data
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        String camelContextName = camelContext.getName();
        if (!usingFixedCamelContextName) {
            camelContextName = QuartzHelper.getQuartzContextName(camelContext);
        }
        LOG.debug("Adding camelContextName={}, endpointUri={} into job data map.", camelContextName, endpointUri);
        jobDataMap.put(QuartzConstants.QUARTZ_CAMEL_CONTEXT_NAME, camelContextName);
        jobDataMap.put(QuartzConstants.QUARTZ_ENDPOINT_URI, endpointUri);
    }

}
