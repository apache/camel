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
package org.apache.camel.routepolicy.quartz2;

import java.io.Serializable;
import java.util.List;

import org.apache.camel.Route;
import org.apache.camel.spi.RoutePolicy;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduledJob implements Job, Serializable, ScheduledRoutePolicyConstants {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledJob.class);

    private static final long serialVersionUID = 26L;

    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        LOG.debug("Running ScheduledJob: jobExecutionContext={}", jobExecutionContext);

        SchedulerContext schedulerContext = getSchedulerContext(jobExecutionContext);
        ScheduledJobState state = (ScheduledJobState) schedulerContext.get(jobExecutionContext.getJobDetail().getKey().toString());
        Action storedAction = state.getAction(); 
        Route storedRoute = state.getRoute();
        
        List<RoutePolicy> policyList = storedRoute.getRouteContext().getRoutePolicyList();
        for (RoutePolicy policy : policyList) {
            try {
                if (policy instanceof ScheduledRoutePolicy) {
                    ((ScheduledRoutePolicy)policy).onJobExecute(storedAction, storedRoute);
                }
            } catch (Exception e) {
                throw new JobExecutionException("Failed to execute Scheduled Job for route " + storedRoute.getId()
                        + " with trigger name: " + jobExecutionContext.getTrigger().getKey(), e);
            }
        }
    }

    private SchedulerContext getSchedulerContext(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        try {
            return jobExecutionContext.getScheduler().getContext();
        } catch (SchedulerException e) {
            throw new JobExecutionException("Failed to obtain scheduler context for job " + jobExecutionContext.getJobDetail().getKey());
        }
    }

}
