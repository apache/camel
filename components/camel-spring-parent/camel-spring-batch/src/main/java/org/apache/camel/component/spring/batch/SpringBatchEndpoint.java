/*
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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobLauncher;

/**
 * Send messages to Spring Batch for further processing.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "spring-batch", title = "Spring Batch", syntax = "spring-batch:jobName",
             remote = false, producerOnly = true, headersClass = SpringBatchConstants.class, category = { Category.WORKFLOW })
public class SpringBatchEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private String jobName;
    @UriParam
    private boolean jobFromHeader;
    @UriParam
    private JobLauncher jobLauncher;
    @UriParam
    private JobRegistry jobRegistry;

    private Job job;

    public SpringBatchEndpoint(String endpointUri, Component component,
                               JobLauncher jobLauncher,
                               String jobName, JobRegistry jobRegistry) {
        super(endpointUri, component);
        this.jobLauncher = jobLauncher;
        this.jobName = jobName;
        this.jobRegistry = jobRegistry;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpringBatchProducer(this, jobLauncher, job, jobRegistry);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (jobLauncher == null) {
            jobLauncher = CamelContextHelper.mandatoryFindSingleByType(getCamelContext(), JobLauncher.class);
        }
        if (job == null && jobName != null && !jobFromHeader) {
            if (jobRegistry != null) {
                job = jobRegistry.getJob(jobName);
            }
            if (job == null) {
                job = CamelContextHelper.mandatoryLookup(getCamelContext(), jobName, Job.class);
            }
        }
    }

    public String getJobName() {
        return jobName;
    }

    /**
     * The name of the Spring Batch job located in the registry.
     */
    public void setJobName(String jobName) {
        this.jobName = jobName;
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

    /**
     * Explicitly defines if the jobName should be taken from the headers instead of the URI.
     */
    public void setJobFromHeader(boolean jobFromHeader) {
        this.jobFromHeader = jobFromHeader;
    }

    public boolean isJobFromHeader() {
        return jobFromHeader;
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
