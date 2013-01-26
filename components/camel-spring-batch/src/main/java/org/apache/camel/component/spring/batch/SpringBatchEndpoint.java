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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;

public class SpringBatchEndpoint extends DefaultEndpoint {

    private String jobLauncherRef;

    private JobLauncher jobLauncher;

    private JobLauncher defaultResolvedJobLauncher;

    private Map<String, JobLauncher> allResolvedJobLaunchers;

    private final Job job;

    public SpringBatchEndpoint(String endpointUri, Component component,
                               JobLauncher jobLauncher, JobLauncher defaultResolvedJobLauncher,
                               Map<String, JobLauncher> allResolvedJobLaunchers,
                               Job job) {
        super(endpointUri, component);
        this.jobLauncher = jobLauncher;
        this.defaultResolvedJobLauncher = defaultResolvedJobLauncher;
        this.allResolvedJobLaunchers = allResolvedJobLaunchers;
        this.job = job;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpringBatchProducer(this, jobLauncher, job);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        if (jobLauncher == null) {
            jobLauncher = resolveJobLauncher();
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

    public void setJobLauncherRef(String jobLauncherRef) {
        this.jobLauncherRef = jobLauncherRef;
    }

}
