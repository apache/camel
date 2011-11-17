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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;

import org.apache.camel.bam.QueryUtils;
import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.bam.rules.ProcessRules;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.JpaCallback;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

/**
 * A timer engine to monitor for expired activities and perform whatever actions
 * are required.
 * 
 * @version 
 */
public class ActivityMonitorEngine extends ServiceSupport implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityMonitorEngine.class);
    private JpaTemplate template;
    private TransactionTemplate transactionTemplate;
    private ProcessRules rules;
    private long windowMillis = 1000L;
    private Thread thread;
    private boolean useLocking;

    public ActivityMonitorEngine(JpaTemplate template, TransactionTemplate transactionTemplate, ProcessRules rules) {
        this.template = template;
        this.transactionTemplate = transactionTemplate;
        this.rules = rules;
    }

    public boolean isUseLocking() {
        return useLocking;
    }

    public void setUseLocking(boolean useLocking) {
        this.useLocking = useLocking;
    }

    public void run() {
        LOG.debug("Starting to poll for timeout events");

        while (!isStopped()) {
            try {
                long now = System.currentTimeMillis();
                long nextPoll = now + windowMillis;
                final Date timeNow = new Date(now);

                transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        Map<String, Object> params = new HashMap<String, Object>(1);
                        params.put("timeNow", timeNow);

                        List<ActivityState> list = CastUtils.cast(template.findByNamedParams("select x from "
                                + QueryUtils.getTypeName(ActivityState.class) + " x where x.timeOverdue < :timeNow", params));
                        for (ActivityState activityState : list) {
                            fireExpiredEvent(activityState);
                        }
                    }
                });

                long timeToSleep = nextPoll - System.currentTimeMillis();
                if (timeToSleep > 0) {
                    LOG.debug("Sleeping for {} millis", timeToSleep);
                    try {
                        Thread.sleep(timeToSleep);
                    } catch (InterruptedException e) {
                        LOG.debug("Caught: " + e, e);
                    }
                }
            } catch (Exception e) {
                LOG.error("Caught: " + e, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void fireExpiredEvent(final ActivityState activityState) {
        LOG.debug("Trying to fire expiration of: {}", activityState);

        template.execute(new JpaCallback() {
            public Object doInJpa(EntityManager entityManager) throws PersistenceException {
                // lets try lock the object first
                if (isUseLocking()) {
                    LOG.info("Attempting to lock: " + activityState);
                    entityManager.lock(activityState, LockModeType.WRITE);
                    LOG.info("Grabbed lock: " + activityState);
                }

                try {
                    rules.processExpired(activityState);
                } catch (Exception e) {
                    LOG.error("Failed to process expiration of: " + activityState + ". Reason: " + e, e);
                }
                activityState.setTimeOverdue(null);
                //activityState.setEscalationLevel(escalateLevel + 1);
                return null;
            }
        });
    }

    protected void doStart() throws Exception {
        rules.start();
        thread = new Thread(this, "ActivityMonitorEngine");
        thread.start();
    }

    protected void doStop() throws Exception {
        if (thread != null) {
            thread = null;
        }
        rules.stop();
    }
}
