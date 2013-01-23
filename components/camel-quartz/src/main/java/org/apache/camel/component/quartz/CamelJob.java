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
package org.apache.camel.component.quartz;

import java.io.Serializable;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;

import static org.apache.camel.util.URISupport.normalizeUri;

/**
 * @version 
 */
public class CamelJob implements Job, Serializable {

    private static final long serialVersionUID = 27L;

    public void execute(JobExecutionContext context) throws JobExecutionException {
        String camelContextName = (String) context.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_CAMEL_CONTEXT_NAME);
        String expectedQuartzEndpointUri = (String) context.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_ENDPOINT_URI);

        SchedulerContext schedulerContext;
        try {
            schedulerContext = context.getScheduler().getContext();
        } catch (SchedulerException e) {
            throw new JobExecutionException("Failed to obtain scheduler context for job " + context.getJobDetail().getName());
        }

        CamelContext camelContext = (CamelContext) schedulerContext.get(QuartzConstants.QUARTZ_CAMEL_CONTEXT + "-" + camelContextName);
        if (camelContext == null) {
            throw new JobExecutionException("No CamelContext could be found with name: " + camelContextName);
        }

        getExpectedQuartzEndpoint(expectedQuartzEndpointUri, camelContext).onJobExecute(context);
    }

    private QuartzEndpoint getExpectedQuartzEndpoint(String expectedQuartzEndpointUri, CamelContext camelContext) throws JobExecutionException {
        try {
            for (Route route : camelContext.getRoutes()) {
                if (route.getEndpoint() instanceof  QuartzEndpoint) {
                    QuartzEndpoint quartzEndpoint = (QuartzEndpoint)route.getEndpoint();
                    if (normalizeUri(quartzEndpoint.getEndpointUri()).equals(normalizeUri(expectedQuartzEndpointUri)) && quartzEndpoint.isStarted()) {
                        return quartzEndpoint;
                    }
                }
            }
        }
        catch (Exception e) {
            throw new JobExecutionException(e);
        }

        throw new JobExecutionException("No QuartzEndpoint could be found with uri: " + expectedQuartzEndpointUri);
    }

}