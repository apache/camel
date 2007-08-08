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
package org.apache.camel.bam.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.bam.rules.ActivityRules;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A concrete {@link Processor} for working on <a
 * href="http://activemq.apache.org/camel/bam.html">BAM</a> which uses JPA as
 * the persistence and uses the {@link ProcessInstance} entity to store the
 * process information.
 * 
 * @version $Revision: $
 */
public class JpaBamProcessor extends JpaBamProcessorSupport<ProcessInstance> {
    private static final transient Log LOG = LogFactory.getLog(JpaBamProcessor.class);

    public JpaBamProcessor(TransactionTemplate transactionTemplate, JpaTemplate template, Expression<Exchange> correlationKeyExpression, ActivityRules activityRules) {
        super(transactionTemplate, template, correlationKeyExpression, activityRules);
    }

    public JpaBamProcessor(TransactionTemplate transactionTemplate, JpaTemplate template, Expression<Exchange> correlationKeyExpression, 
                           ActivityRules activityRules, Class<ProcessInstance> entitytype) {
        super(transactionTemplate, template, correlationKeyExpression, activityRules, entitytype);
    }

    protected void processEntity(Exchange exchange, ProcessInstance process) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing process instance: " + process);
        }

        // lets force the lazy creation of this activity
        ActivityRules rules = getActivityRules();
        ActivityState state = process.getOrCreateActivityState(rules);

        state.processExchange(rules, new ProcessContext(exchange, rules, state));

        rules.getProcessRules().processExchange(exchange, process);
    }
}
