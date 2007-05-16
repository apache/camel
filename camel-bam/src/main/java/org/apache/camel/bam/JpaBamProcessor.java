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

import org.apache.camel.bam.model.*;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version $Revision: $
 */
public class JpaBamProcessor extends JpaBamProcessorSupport<ProcessInstance> {
    private static final transient Log log = LogFactory.getLog(JpaBamProcessor.class);

    public JpaBamProcessor(TransactionTemplate transactionTemplate, JpaTemplate template, Expression<Exchange> correlationKeyExpression, ActivityRules activityRules) {
        super(transactionTemplate, template, correlationKeyExpression, activityRules);
    }

    public JpaBamProcessor(TransactionTemplate transactionTemplate, JpaTemplate template, Expression<Exchange> correlationKeyExpression, ActivityRules activityRules, Class<ProcessInstance> entitytype) {
        super(transactionTemplate, template, correlationKeyExpression, activityRules, entitytype);
    }

    protected void processEntity(Exchange exchange, ProcessInstance process) throws Exception {
        log.info("Processing entity! - attempting to get the current state for process: " + process);

        ActivityState state = process.getActivityState(getActivity());
        log.info("Found activity: "+ state);

        if (state == null) {
            state = createActivityState(exchange, process);
            state.setProcess(process);

            log.info("Storing activity: "+ state + " with process: " + state.getProcess());
            //getTemplate().persist(state);
        }
        ProcessContext context = new ProcessContext(exchange, getActivity(), state);

        state.processExchange(getActivity(), context);
    }

    protected ActivityState createActivityState(Exchange exchange, ProcessInstance process) {
        return new ActivityState();
    }
}
