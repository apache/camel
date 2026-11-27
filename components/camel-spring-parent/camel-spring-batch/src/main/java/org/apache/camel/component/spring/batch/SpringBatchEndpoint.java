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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
 * Launch Spring Batch jobs, with the message headers as the job parameters.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "spring-batch", title = "Spring Batch", syntax = "spring-batch:jobName",
             remote = false, producerOnly = true, headersClass = SpringBatchConstants.class, category = { Category.WORKFLOW })
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not read: the job parameters are the message headers (Date, Long and"
                                                + " Double values as such, other values as text; headers with a null value are skipped)"),
           producerOut = @BodyType(value = Carrier.POJO, javaType = "org.springframework.batch.core.job.JobExecution",
                                   description = "The JobExecution of the launched job, with its status and exit status; the"
                                                 + " headers stay as they were"))
public class SpringBatchEndpoint extends DefaultEndpoint {

    @UriPath(description = "The name of the Spring Batch job to launch. When the endpoint starts, the job is looked up in the"
                           + " jobRegistry, when one is set, and else in the Camel registry as a Job bean; the endpoint fails"
                           + " to start when the job is not found. Not used when jobFromHeader is true.")
    @Metadata(required = true)
    private String jobName;
    @UriParam(description = "Whether the producer takes the name of the job to launch from the CamelSpringBatchJobName header"
                            + " of each message instead of the jobName path, and looks the job up for every message. The"
                            + " exchange fails when the header is missing. When false, that header is ignored.")
    private boolean jobFromHeader;
    @UriParam(description = "The JobLauncher that runs the job, instead of the jobLauncher of the component. When neither is"
                            + " set, the endpoint uses the single JobLauncher of the registry and fails to start when there"
                            + " is none or more than one.")
    private JobLauncher jobLauncher;
    @UriParam(description = "The JobRegistry in which the job is looked up by its name, instead of the jobRegistry of the"
                            + " component. A job that is not in it is looked up in the Camel registry as a Job bean.")
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

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public JobLauncher getJobLauncher() {
        return jobLauncher;
    }

    public void setJobLauncher(JobLauncher jobLauncher) {
        this.jobLauncher = jobLauncher;
    }

    public void setJobFromHeader(boolean jobFromHeader) {
        this.jobFromHeader = jobFromHeader;
    }

    public boolean isJobFromHeader() {
        return jobFromHeader;
    }

    public JobRegistry getJobRegistry() {
        return jobRegistry;
    }

    public void setJobRegistry(JobRegistry jobRegistry) {
        this.jobRegistry = jobRegistry;
    }

}
