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

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuartzScheduledPollConsumerJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(QuartzScheduledPollConsumerJob.class);

    public QuartzScheduledPollConsumerJob() {
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        LOG.trace("Execute job: {}", jobExecutionContext);

        Runnable task = (Runnable) jobExecutionContext.getJobDetail().getJobDataMap().get("task");
        if (task != null) {
            LOG.trace("Running task: {}", task);
            task.run();
        }
    }
}
