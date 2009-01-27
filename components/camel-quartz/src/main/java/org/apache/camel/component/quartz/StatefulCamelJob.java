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

import org.apache.camel.CamelContext;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.StatefulJob;

/**
 * @author martin.gilday
 *
 */
public class StatefulCamelJob implements StatefulJob {

    /* (non-Javadoc)
     * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
     */
    public void execute(final JobExecutionContext context) throws JobExecutionException {

        SchedulerContext schedulerContext;
        try {
            schedulerContext = context.getScheduler().getContext();
        } catch (SchedulerException e) {
            throw new JobExecutionException("Failed to obtain scheduler context for job " + context.getJobDetail().getName());
        }

        CamelContext camelContext = (CamelContext) schedulerContext.get(QuartzEndpoint.CONTEXT_KEY);
        String endpointUri = (String) context.getJobDetail().getJobDataMap().get(QuartzEndpoint.ENDPOINT_KEY);
        QuartzEndpoint quartzEndpoint =    (QuartzEndpoint) camelContext.getEndpoint(endpointUri);
        quartzEndpoint.onJobExecute(context);
    }

}
