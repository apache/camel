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
package org.apache.camel.routepolicy.quartz;

import java.io.Serializable;

import org.apache.camel.Route;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;

public class ScheduledJob implements Job, Serializable, ScheduledRoutePolicyConstants {
    private static final long serialVersionUID = 26L;
    private Route storedRoute;    
    
    /* (non-Javadoc)
     * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
     */
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        SchedulerContext schedulerContext;
        try {
            schedulerContext = jobExecutionContext.getScheduler().getContext();
        } catch (SchedulerException e) {
            throw new JobExecutionException("Failed to obtain scheduler context for job " + jobExecutionContext.getJobDetail().getName());
        }
        
        Action storedAction = (Action) schedulerContext.get(SCHEDULED_ACTION);
        storedRoute = (Route) schedulerContext.get(SCHEDULED_ROUTE);
        
        ScheduledRoutePolicy policy = (ScheduledRoutePolicy) storedRoute.getRouteContext().getRoutePolicy();
        try {
            policy.onJobExecute(storedAction, storedRoute);
        } catch (Exception e) {
            throw new JobExecutionException("Failed to execute Scheduled Job for route " + storedRoute.getId() + " with trigger name: " + jobExecutionContext.getTrigger().getFullName());
        }
    }


}
