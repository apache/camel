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
package org.apache.camel.bam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManagerFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.bam.model.ActivityDefinition;
import org.apache.camel.bam.model.ProcessDefinition;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.bam.processor.ActivityMonitorEngine;
import org.apache.camel.bam.processor.JpaBamProcessor;
import org.apache.camel.bam.rules.ProcessRules;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A builder of a process definition
 * 
 * @version 
 */
public abstract class ProcessBuilder extends RouteBuilder {
    private static int processCounter;
    private EntityManagerFactory entityManagerFactory;
    private EntityManagerTemplate entityManagerTemplate;
    private TransactionTemplate transactionTemplate;
    private String processName;
    private final List<ActivityBuilder> activityBuilders = new ArrayList<ActivityBuilder>();
    private Class<ProcessInstance> entityType = ProcessInstance.class;
    private final ProcessRules processRules = new ProcessRules();
    private volatile ProcessDefinition processDefinition;
    private ActivityMonitorEngine engine;

    protected ProcessBuilder() {
    }

    protected ProcessBuilder(EntityManagerFactory entityManagerFactory, TransactionTemplate transactionTemplate) {
        this(entityManagerFactory, transactionTemplate, createProcessName());
    }

    protected ProcessBuilder(EntityManagerFactory entityManagerFactory, TransactionTemplate transactionTemplate, String processName) {
        setEntityManagerFactory(entityManagerFactory);
        this.transactionTemplate = transactionTemplate;
        this.processName = processName;
    }

    protected static synchronized String createProcessName() {
        return "Process-" + (++processCounter);
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
    public ProcessBuilder entityType(Class<ProcessInstance> entityType) {
        this.entityType = entityType;
        return this;
    }

    public Processor createActivityProcessor(ActivityBuilder activityBuilder) {
        notNull(entityManagerFactory, "entityManagerFactory");
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                processRules.setProcessDefinition(getProcessDefinition());
            }
        });
        return new JpaBamProcessor(transactionTemplate, entityManagerFactory, activityBuilder.getCorrelationExpression(), activityBuilder.getActivityRules(), getEntityType());
    }

    // Properties
    // -----------------------------------------------------------------------
    public List<ActivityBuilder> getActivityBuilders() {
        return activityBuilders;
    }

    public Class<ProcessInstance> getEntityType() {
        return entityType;
    }

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.entityManagerTemplate = new EntityManagerTemplate(entityManagerFactory);
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    public void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public ProcessRules getProcessRules() {
        return processRules;
    }

    public String getProcessName() {
        if (processName == null) {
            processName = createProcessName();
        }
        return processName;
    }

    public synchronized ProcessDefinition getProcessDefinition() {
        if (processDefinition == null) {
            processDefinition = findOrCreateProcessDefinition();
        }
        return processDefinition;
    }

    public void setProcessDefinition(ProcessDefinition processDefinition) {
        this.processDefinition = processDefinition;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected void populateRoutes() throws Exception {
        ObjectHelper.notNull(entityManagerFactory, "entityManagerFactory", this);
        ObjectHelper.notNull(getTransactionTemplate(), "transactionTemplate", this);

        // add the monitoring service - should there be an easier way??
        if (engine == null) {
            engine = new ActivityMonitorEngine(entityManagerFactory, getTransactionTemplate(), getProcessRules());
        }
        CamelContext camelContext = getContext();
        if (camelContext instanceof DefaultCamelContext) {
            DefaultCamelContext defaultCamelContext = (DefaultCamelContext) camelContext;
            defaultCamelContext.addService(engine);
        }

        // create the routes for the activities
        for (ActivityBuilder builder : activityBuilders) {
            from(builder.getEndpoint()).process(builder.getProcessor());
        }

        super.populateRoutes();
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    public ActivityDefinition findOrCreateActivityDefinition(String activityName) {
        ProcessDefinition definition = getProcessDefinition();

        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("definition", definition);
        params.put("name", activityName);

        List<ActivityDefinition> list = entityManagerTemplate.find(ActivityDefinition.class, "select x from "
            + QueryUtils.getTypeName(ActivityDefinition.class) + " x where x.processDefinition = :definition and x.name = :name", params);
        if (!list.isEmpty()) {
            return list.get(0);
        } else {
            ActivityDefinition answer = new ActivityDefinition();
            answer.setName(activityName);
            answer.setProcessDefinition(ProcessDefinition.getRefreshedProcessDefinition(entityManagerTemplate, definition));
            entityManagerTemplate.persist(answer);
            return answer;
        }
    }

    protected ProcessDefinition findOrCreateProcessDefinition() {
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("name", processName);

        List<ProcessDefinition> list = entityManagerTemplate.find(ProcessDefinition.class, "select x from "
            + QueryUtils.getTypeName(ProcessDefinition.class) + " x where x.name = :name", params);
        if (!list.isEmpty()) {
            return list.get(0);
        } else {
            ProcessDefinition answer = new ProcessDefinition();
            answer.setName(processName);
            entityManagerTemplate.persist(answer);
            return answer;
        }
    }
}
