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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.bam.model.ActivityDefinition;
import org.apache.camel.bam.model.ProcessDefinition;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.bam.processor.ActivityMonitorEngine;
import org.apache.camel.bam.processor.JpaBamProcessor;
import org.apache.camel.bam.processor.JpaBamProcessorSupport;
import org.apache.camel.bam.rules.ProcessRules;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A builder of a process definition
 * 
 * @version 
 */
public abstract class ProcessBuilder extends RouteBuilder {
    private static final transient Logger LOG = LoggerFactory.getLogger(ProcessBuilder.class);
    private static int processCounter;
    private JpaTemplate jpaTemplate;
    private TransactionTemplate transactionTemplate;
    private String processName;
    private List<ActivityBuilder> activityBuilders = new ArrayList<ActivityBuilder>();
    private Class<ProcessInstance> entityType = ProcessInstance.class;
    private ProcessRules processRules = new ProcessRules();
    private ProcessDefinition processDefinition;
    private ActivityMonitorEngine engine;

    protected ProcessBuilder() {
    }

    protected ProcessBuilder(JpaTemplate jpaTemplate, TransactionTemplate transactionTemplate) {
        this(jpaTemplate, transactionTemplate, createProcessName());
    }

    protected ProcessBuilder(JpaTemplate jpaTemplate, TransactionTemplate transactionTemplate, String processName) {
        this.jpaTemplate = jpaTemplate;
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
        notNull(jpaTemplate, "jpaTemplate");
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                processRules.setProcessDefinition(getProcessDefinition());
            }
        });
        return new JpaBamProcessor(getTransactionTemplate(), getJpaTemplate(), activityBuilder.getCorrelationExpression(), activityBuilder.getActivityRules(), getEntityType());
    }

    // Properties
    // -----------------------------------------------------------------------
    public List<ActivityBuilder> getActivityBuilders() {
        return activityBuilders;
    }

    public Class<ProcessInstance> getEntityType() {
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

    public void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public ProcessRules getProcessRules() {
        return processRules;
    }

    public String getProcessName() {
        return processName;
    }

    public ProcessDefinition getProcessDefinition() {
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
        ObjectHelper.notNull(getJpaTemplate(), "jpaTemplate", this);
        ObjectHelper.notNull(getTransactionTemplate(), "transactionTemplate", this);

        // lets add the monitoring service - should there be an easier way??
        if (engine == null) {
            engine = new ActivityMonitorEngine(getJpaTemplate(), getTransactionTemplate(), getProcessRules());
        }
        CamelContext camelContext = getContext();
        if (camelContext instanceof DefaultCamelContext) {
            DefaultCamelContext defaultCamelContext = (DefaultCamelContext) camelContext;
            defaultCamelContext.addService(engine);
        }

        // lets create the routes for the activites
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

        List<ActivityDefinition> list = CastUtils.cast(jpaTemplate.findByNamedParams("select x from "
            + QueryUtils.getTypeName(ActivityDefinition.class) + " x where x.processDefinition = :definition and x.name = :name", params));
        if (!list.isEmpty()) {
            return list.get(0);
        } else {
            ActivityDefinition answer = new ActivityDefinition();
            answer.setName(activityName);
            answer.setProcessDefinition(ProcessDefinition.getRefreshedProcessDefinition(jpaTemplate, definition));
            jpaTemplate.persist(answer);
            return answer;
        }
    }

    protected ProcessDefinition findOrCreateProcessDefinition() {
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("name", processName);

        List<ProcessDefinition> list = CastUtils.cast(jpaTemplate.findByNamedParams("select x from "
            + QueryUtils.getTypeName(ProcessDefinition.class) + " x where x.name = :name", params));
        if (!list.isEmpty()) {
            return list.get(0);
        } else {
            ProcessDefinition answer = new ProcessDefinition();
            answer.setName(processName);
            jpaTemplate.persist(answer);
            return answer;
        }
    }
}
