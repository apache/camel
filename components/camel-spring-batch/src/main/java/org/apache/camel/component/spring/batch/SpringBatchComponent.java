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
package org.apache.camel.component.spring.batch;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.CamelContextHelper;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;

public class SpringBatchComponent extends DefaultComponent {

    private static final String DEFAULT_JOB_LAUNCHER_REF_NAME = "jobLauncher";

    private JobLauncher jobLauncher;

    private JobLauncher defaultResolvedJobLauncher;

    private Map<String, JobLauncher> allResolvedJobLaunchers;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Job resolvedJob = CamelContextHelper.mandatoryLookup(getCamelContext(), remaining, Job.class);
        return new SpringBatchEndpoint(uri, this, jobLauncher, defaultResolvedJobLauncher, allResolvedJobLaunchers, resolvedJob);
    }

    @Override
    protected void doStart() throws Exception {
        defaultResolvedJobLauncher = getCamelContext().getRegistry().lookupByNameAndType(DEFAULT_JOB_LAUNCHER_REF_NAME, JobLauncher.class);
        allResolvedJobLaunchers = getCamelContext().getRegistry().findByTypeWithName(JobLauncher.class);
    }

    public void setJobLauncher(JobLauncher jobLauncher) {
        this.jobLauncher = jobLauncher;
    }

}