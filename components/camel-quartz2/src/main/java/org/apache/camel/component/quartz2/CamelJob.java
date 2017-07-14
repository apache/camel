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

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a Quartz Job that is scheduled by QuartzEndpoint's Consumer and will call it to
 * produce a QuartzMessage sending to a route.
 *
 */
public class CamelJob implements Job {
    private static final Logger LOG = LoggerFactory.getLogger(CamelJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Exchange exchange = null;
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Running CamelJob jobExecutionContext={}", context);
            }

            CamelContext camelContext = getCamelContext(context);
            QuartzEndpoint endpoint = lookupQuartzEndpoint(camelContext, context);
            exchange = endpoint.createExchange();
            exchange.setIn(new QuartzMessage(exchange, context));
            endpoint.getConsumerLoadBalancer().process(exchange);

            if (exchange.getException() != null) {
                throw new JobExecutionException(exchange.getException());
            }
        } catch (Exception e) {
            if (exchange != null) {
                LOG.error(CamelExchangeException.createExceptionMessage("Error processing exchange", exchange, e));
            } else {
                LOG.error("Failed to execute CamelJob.", e);
            }

            // and rethrow to let quartz handle it
            if (e instanceof JobExecutionException) {
                throw (JobExecutionException) e;
            }
            throw new JobExecutionException(e);
        }
    }

    protected CamelContext getCamelContext(JobExecutionContext context) throws JobExecutionException {
        SchedulerContext schedulerContext = getSchedulerContext(context);
        String camelContextName = context.getMergedJobDataMap().getString(QuartzConstants.QUARTZ_CAMEL_CONTEXT_NAME);
        CamelContext result = (CamelContext)schedulerContext.get(QuartzConstants.QUARTZ_CAMEL_CONTEXT + "-" + camelContextName);
        if (result == null) {
            throw new JobExecutionException("No CamelContext could be found with name: " + camelContextName);
        }
        return result;
    }

    protected SchedulerContext getSchedulerContext(JobExecutionContext context) throws JobExecutionException {
        try {
            return context.getScheduler().getContext();
        } catch (SchedulerException e) {
            throw new JobExecutionException("Failed to obtain scheduler context for job " + context.getJobDetail().getKey());
        }
    }

    protected QuartzEndpoint lookupQuartzEndpoint(CamelContext camelContext, JobExecutionContext quartzContext) throws JobExecutionException {
        TriggerKey triggerKey = quartzContext.getTrigger().getKey();
        JobDetail jobDetail = quartzContext.getJobDetail(); 
        JobKey jobKey =  jobDetail.getKey();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Looking up existing QuartzEndpoint with triggerKey={}", triggerKey);
        }
        
        // check all active routes for the quartz endpoint this task matches
        // as we prefer to use the existing endpoint from the routes
        for (Route route : camelContext.getRoutes()) {
            Endpoint endpoint = route.getEndpoint();
            if (endpoint instanceof DelegateEndpoint) {
                endpoint = ((DelegateEndpoint)endpoint).getEndpoint();   
            }
            if (endpoint instanceof QuartzEndpoint) {
                QuartzEndpoint quartzEndpoint = (QuartzEndpoint) endpoint;
                TriggerKey checkTriggerKey = quartzEndpoint.getTriggerKey();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Checking route endpoint={} with checkTriggerKey={}", quartzEndpoint, checkTriggerKey);
                }
                if (triggerKey.equals(checkTriggerKey)
                    || (jobDetail.requestsRecovery() && jobKey.getGroup().equals(checkTriggerKey.getGroup()) && jobKey.getName().equals(checkTriggerKey.getName()))) {
                    return quartzEndpoint;
                }
            }
        }

        // fallback and lookup existing from registry (eg maybe a @Consume POJO with a quartz endpoint, and thus not from a route)
        String endpointUri = quartzContext.getMergedJobDataMap().getString(QuartzConstants.QUARTZ_ENDPOINT_URI);
        
        QuartzEndpoint result = null;

        // Even though the same camelContext.getEndpoint call, but if/else display different log.
        if (camelContext.hasEndpoint(endpointUri) != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Getting Endpoint from camelContext.");
            }
            result = camelContext.getEndpoint(endpointUri, QuartzEndpoint.class);
        } else if ((result = searchForEndpointMatch(camelContext, endpointUri)) != null) { 
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found match for endpoint URI = " + endpointUri + " by searching endpoint list.");
            }        
        } else {
            LOG.warn("Cannot find existing QuartzEndpoint with uri: {}. Creating new endpoint instance.", endpointUri);
            result = camelContext.getEndpoint(endpointUri, QuartzEndpoint.class);
        }
        if (result == null) {
            throw new JobExecutionException("No QuartzEndpoint could be found with endpointUri: " + endpointUri);
        }

        return result;
    }

    protected QuartzEndpoint searchForEndpointMatch(CamelContext camelContext, String endpointUri) {
        Collection<Endpoint> endpoints = camelContext.getEndpoints();
        for (Endpoint endpoint : endpoints) {
            if (endpointUri.equals(endpoint.getEndpointUri())) {
                return (QuartzEndpoint) endpoint;
            }
        }
        return null;
    }
}
