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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.URISupport.normalizeUri;

/**
 * @version 
 */
public class CamelJob implements Job, Serializable {

    private static final transient Logger LOG = LoggerFactory.getLogger(CamelJob.class);
    private static final long serialVersionUID = 26L;

    public void execute(JobExecutionContext context) throws JobExecutionException {
        String camelContextName = (String) context.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_CAMEL_CONTEXT_NAME);
        String endpointUri = (String) context.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_ENDPOINT_URI);

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

        QuartzEndpoint endpoint = lookupQuartzEndpoint(camelContext, endpointUri);
        if (endpoint == null) {
            throw new JobExecutionException("No QuartzEndpoint could be found with uri: " + endpointUri);
        }
        endpoint.onJobExecute(context);
    }

    private QuartzEndpoint lookupQuartzEndpoint(CamelContext camelContext, String endpointUri) throws JobExecutionException {
        try {
            String targetUri = normalizeUri(endpointUri);

            // check all active routes for the quartz endpoint this task matches
            // as we prefer to use the existing endpoint from the routes
            for (Route route : camelContext.getRoutes()) {
                if (route.getEndpoint() instanceof QuartzEndpoint) {
                    if (normalizeUri(route.getEndpoint().getEndpointUri()).equals(targetUri)) {
                        return (QuartzEndpoint) route.getEndpoint();
                    }
                }
            }
        } catch (Exception e) {
            throw new JobExecutionException("Error lookup up existing QuartzEndpoint with uri: " + endpointUri, e);
        }

        // fallback and lookup existing from registry (eg maybe a @Consume POJO with a quartz endpoint, and thus not from a route)
        if (camelContext.hasEndpoint(endpointUri) != null) {
            return camelContext.getEndpoint(endpointUri, QuartzEndpoint.class);
        } else {
            LOG.warn("Cannot find existing QuartzEndpoint with uri: {}. Creating new endpoint instance.", endpointUri);
            return camelContext.getEndpoint(endpointUri, QuartzEndpoint.class);
        }
    }

}