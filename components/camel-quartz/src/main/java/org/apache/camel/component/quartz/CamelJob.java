/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.quartz;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * @version $Revision: 1.1 $
 */
public class CamelJob implements Job {
    public void execute(JobExecutionContext context) throws JobExecutionException {
        QuartzEndpoint component = (QuartzEndpoint) context.getJobDetail().getJobDataMap().get(QuartzEndpoint.ENDPOINT_KEY);
        if (component == null) {
            throw new JobExecutionException("No quartz endpoint available for key: " + QuartzEndpoint.ENDPOINT_KEY + ". Bad job data map");
        }
        component.onJobExecute(context);
    }
}