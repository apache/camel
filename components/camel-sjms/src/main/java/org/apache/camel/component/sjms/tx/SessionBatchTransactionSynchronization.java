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
package org.apache.camel.component.sjms.tx;

import java.util.TimerTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.TransactionCommitStrategy;
import org.apache.camel.component.sjms.taskmanager.TimedTaskManager;
import org.apache.camel.spi.Synchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SessionTransactionSynchronization is called at the completion of each
 * {@link org.apache.camel.Exchange}.
 */
public class SessionBatchTransactionSynchronization implements Synchronization {
    private static final Logger LOG = LoggerFactory.getLogger(SessionBatchTransactionSynchronization.class);
    private Session session;
    private final TransactionCommitStrategy commitStrategy;
    private long batchTransactionTimeout = 5000;
    private TimeoutTask currentTask;
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private final TimedTaskManager timedTaskManager;

    public SessionBatchTransactionSynchronization(TimedTaskManager timedTaskManager,
                                                  Session session, TransactionCommitStrategy commitStrategy, long batchTransactionTimeout) {
        this.timedTaskManager = timedTaskManager;
        this.session = session;
        if (commitStrategy == null) {
            this.commitStrategy = new DefaultTransactionCommitStrategy();
        } else {
            this.commitStrategy = commitStrategy;
        }
        if (batchTransactionTimeout > 0) {
            this.batchTransactionTimeout = batchTransactionTimeout;
            createTask();
        }
    }

    @Override
    public void onFailure(Exchange exchange) {
        try {
            lock.readLock().lock();
            if (commitStrategy.rollback(exchange)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Processing failure of Exchange id:{}", exchange.getExchangeId());
                }
                if (session != null && session.getTransacted()) {
                    session.rollback();
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to rollback the session: " + e.getMessage() + ". This exception will be ignored.", e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void onComplete(Exchange exchange) {
        try {
            lock.readLock().lock();
            if (commitStrategy.commit(exchange)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Processing completion of Exchange id:{}", exchange.getExchangeId());
                }
                if (session != null && session.getTransacted()) {
                    session.commit();
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to commit the session: " + e.getMessage() + ". This exception will be ignored.", e);
            exchange.setException(e);
        } finally {
            lock.readLock().unlock();
        }
        resetTask();
    }

    private void createTask() {
        try {
            lock.writeLock().lock();
            currentTask = new TimeoutTask();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void resetTask() {
        try {
            lock.writeLock().lock();
            currentTask.cancel();
            currentTask = new TimeoutTask();
        } finally {
            lock.writeLock().unlock();
        }
        timedTaskManager.addTask(currentTask, batchTransactionTimeout);
    }

    public final class TimeoutTask extends TimerTask {

        private TimeoutTask() {
        }

        /**
         * When the timer executes, either commits or rolls back the session
         * transaction.
         */
        public void run() {
            LOG.debug("Batch Transaction Timer expired");
            try {
                lock.writeLock().lock();
                LOG.trace("Committing the current transactions");
                try {
                    if (session != null && session.getTransacted()) {
                        session.commit();
                    }
                    ((BatchTransactionCommitStrategy) commitStrategy).reset();
                } catch (Exception e) {
                    LOG.warn("Failed to commit the session during timeout: " + e.getMessage() + ". This exception will be ignored.", e);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public boolean cancel() {
            LOG.trace("Cancelling the TimeoutTask");
            return super.cancel();
        }
    }
}
