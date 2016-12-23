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
import org.apache.camel.impl.UriEndpointComponent;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;

public class SpringBatchComponent extends UriEndpointComponent {

    private static final String DEFAULT_JOB_LAUNCHER_REF_NAME = "jobLauncher";

    private JobLauncher defaultResolvedJobLauncher;
    private Map<String, JobLauncher> allResolvedJobLaunchers;

    private JobLauncher jobLauncher;
    private JobRegistry jobRegistry;

    public SpringBatchComponent() {
        super(SpringBatchEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SpringBatchEndpoint endpoint = new SpringBatchEndpoint(uri, this, jobLauncher, defaultResolvedJobLauncher, 
                allResolvedJobLaunchers, remaining, jobRegistry);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        defaultResolvedJobLauncher = getCamelContext().getRegistry().lookupByNameAndType(DEFAULT_JOB_LAUNCHER_REF_NAME, JobLauncher.class);
        allResolvedJobLaunchers = getCamelContext().getRegistry().findByTypeWithName(JobLauncher.class);
    }

    public JobLauncher getJobLauncher() {
        return jobLauncher;
    }

    /**
     * Explicitly specifies a JobLauncher to be used.
     */
    public void setJobLauncher(JobLauncher jobLauncher) {
        this.jobLauncher = jobLauncher;
    }

    public JobRegistry getJobRegistry() {
        return jobRegistry;
    }

    /**
     * Explicitly specifies a JobRegistry to be used.
     */    
    public void setJobRegistry(JobRegistry jobRegistry) {
        this.jobRegistry = jobRegistry;
    }
}