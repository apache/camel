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
package org.apache.camel.pollconsumer.quartz2;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.component.quartz2.CamelJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class QuartzScheduledPollConsumerJob extends CamelJob {

    private static final Logger LOG = LoggerFactory.getLogger(QuartzScheduledPollConsumerJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        LOG.trace("Execute job: {}", context);

        CamelContext camelContext = getCamelContext(context);

        Runnable task = (Runnable) context.getJobDetail().getJobDataMap().get("task");

        if (task == null) {
            // if not task then use the route id to lookup the consumer to be used as the task
            String routeId = (String) context.getJobDetail().getJobDataMap().get("routeId");
            if (routeId != null && camelContext != null) {
                // find the consumer
                for (Route route : camelContext.getRoutes()) {
                    if (route.getId().equals(routeId)) {
                        Consumer consumer = route.getConsumer();
                        if (consumer instanceof Runnable) {
                            task = (Runnable) consumer;
                            break;
                        }
                    }
                }
            }
        }

        if (task != null) {
            LOG.trace("Running task: {}", task);
            task.run();
        }
    }
}
