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
package org.apache.camel.bam;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.LifecycleProcessor;
import static org.apache.camel.util.ObjectHelper.notNull;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder of a process definition
 *
 * @version $Revision: $
 */
public abstract class ProcessBuilder extends RouteBuilder {
    private static int processCounter;
    private JpaTemplate jpaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String processName;
    private List<ActivityBuilder> activityBuilders = new ArrayList<ActivityBuilder>();
    private Class entityType = ProcessInstance.class;
    private ProcessRules processRules = new ProcessRules();

    protected ProcessBuilder(JpaTemplate jpaTemplate, TransactionTemplate transactionTemplate) {
        this(jpaTemplate, transactionTemplate, createProcessName());
    }

    protected static synchronized String createProcessName() {
        return "Process-" + (++processCounter);
    }

    protected ProcessBuilder(JpaTemplate jpaTemplate, TransactionTemplate transactionTemplate, String processName) {
        this.jpaTemplate = jpaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.processName = processName;
    }

    public ActivityBuilder activity(String endpointUri) {
        return activity(endpoint(endpointUri));
    }

    public ActivityBuilder activity(Endpoint endpoint) {
        ActivityBuilder answer = new ActivityBuilder(this, endpoint);
        activityBuilders.add(answer);
        return answer;
    }

    /**
     * Sets the process entity type used to perform state management
     */
    public ProcessBuilder entityType(Class entityType) {
        this.entityType = entityType;
        return this;
    }

    public Processor createActivityProcessor(ActivityBuilder activityBuilder) {
        notNull(jpaTemplate, "jpaTemplate");
        return new JpaBamProcessor(getTransactionTemplate(), getJpaTemplate(), activityBuilder.getCorrelationExpression(), activityBuilder.getActivityRules(), getEntityType());
    }

    // Properties
    //-----------------------------------------------------------------------
    public List<ActivityBuilder> getActivityBuilders() {
        return activityBuilders;
    }

    public Class getEntityType() {
        return entityType;
    }

    public JpaTemplate getJpaTemplate() {
        return jpaTemplate;
    }

    public void setJpaTemplate(JpaTemplate jpaTemplate) {
        this.jpaTemplate = jpaTemplate;
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    public ProcessRules getProcessRules() {
        return processRules;
    }

    public String getProcessName() {
        return processName;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void populateRoutes(List<Route> routes) throws Exception {
        boolean first = true;
        for (ActivityBuilder builder : activityBuilders) {
            Endpoint from = builder.getEndpoint();
            Processor processor = builder.createProcessor();
            if (processor == null) {
                throw new IllegalArgumentException("No processor created for ActivityBuilder: " + builder);
            }

            // lets add extra services to the first processor lifecycle
            // TODO this is a little bit of a hack; we might want to add an ability to add dependent services to routes etc
            if (first) {
                processor = new LifecycleProcessor(processor, new ActivityMonitorEngine(getJpaTemplate(), getTransactionTemplate(), getProcessRules()));
                first = false;
            }
            routes.add(new Route(from, processor));
        }
    }
}
