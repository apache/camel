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

import java.util.Map;

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
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;

/**
 * The spring-batch component allows to send messages to Spring Batch for further processing.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "spring-batch", title = "Spring Batch", syntax = "spring-batch:jobName", producerOnly = true, label = "spring,batch,scheduling")
public class SpringBatchEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private String jobName;

    @UriParam
    private boolean jobFromHeader;

    /**
     * @deprecated will be removed in Camel 3.0
     * use jobLauncher instead
     */
    @Deprecated
    private String jobLauncherRef;

    @UriParam
    private JobLauncher jobLauncher;

    private JobLauncher defaultResolvedJobLauncher;
    private Map<String, JobLauncher> allResolvedJobLaunchers;
    private Job job;
    
    @UriParam
    private JobRegistry jobRegistry;

    public SpringBatchEndpoint(String endpointUri, Component component,
                               JobLauncher jobLauncher, JobLauncher defaultResolvedJobLauncher,
                               Map<String, JobLauncher> allResolvedJobLaunchers, String jobName,
                               JobRegistry jobRegistry) {
        super(endpointUri, component);
        this.jobLauncher = jobLauncher;
        this.defaultResolvedJobLauncher = defaultResolvedJobLauncher;
        this.allResolvedJobLaunchers = allResolvedJobLaunchers;
        this.jobName = jobName;
        this.jobRegistry = jobRegistry;
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
    protected void doInit() throws Exception {
        super.doInit();

        if (jobLauncher == null) {
            jobLauncher = resolveJobLauncher();
        } 
        if (job == null && jobName != null && !jobFromHeader) {
            if (jobRegistry != null) {
                job = jobRegistry.getJob(jobName);
            } else {
                job = CamelContextHelper.mandatoryLookup(getCamelContext(), jobName, Job.class);
            }
        }
    }

    private JobLauncher resolveJobLauncher() {
        if (jobLauncherRef != null) {
            JobLauncher jobLauncher = getCamelContext().getRegistry().lookupByNameAndType(jobLauncherRef, JobLauncher.class);
            if (jobLauncher == null) {
                throw new IllegalStateException(String.format("No JobLauncher named %s found in the registry.", jobLauncherRef));
            }
            return jobLauncher;
        }

        if (defaultResolvedJobLauncher != null) {
            return defaultResolvedJobLauncher;
        }

        if (allResolvedJobLaunchers.size() == 1) {
            return allResolvedJobLaunchers.values().iterator().next();
        } else if (allResolvedJobLaunchers.size() > 1) {
            throw new IllegalStateException("Expected single jobLauncher instance. Found: " + allResolvedJobLaunchers.size());
        }

        throw new IllegalStateException("Cannot find Spring Batch JobLauncher.");
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

    @Deprecated
    public String getJobLauncherRef() {
        return jobLauncherRef;
    }

    /**
     * Explicitly specifies a JobLauncher to be used looked up from the registry.
     */
    @Deprecated
    public void setJobLauncherRef(String jobLauncherRef) {
        this.jobLauncherRef = jobLauncherRef;
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
