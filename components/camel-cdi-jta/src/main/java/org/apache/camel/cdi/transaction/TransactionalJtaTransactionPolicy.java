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
package org.apache.camel.cdi.transaction;

import java.util.Objects;
import java.util.stream.Stream;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.apache.camel.CamelException;
import org.apache.camel.jta.JtaTransactionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;

/**
 * Helper methods for transaction handling
 *
 * This class requires the resource {@link TransactionManager} to be available through any of JNDI locations:
 * &quot;java:comp/TransactionManager&quot;, &quot;java:appserver/TransactionManager&quot;,
 * &quot;java:pm/TransactionManager&quot;, &quot;java:/TransactionManager&quot;
 */
public abstract class TransactionalJtaTransactionPolicy extends JtaTransactionPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionalJtaTransactionPolicy.class);
    private static final String[] TRANSACTION_MANAGER_JNDI_NAMES = new String[] {
            "java:comp/TransactionManager",
            "java:appserver/TransactionManager",
            "java:pm/TransactionManager",
            "java:/TransactionManager"
    };
    private static final String[] METHODS = new String[] {
            "org.openejb.OpenEJB.getTransactionManager",
            "com.arjuna.ats.jta.TransactionManager.transactionManager",
            "com.bluestone.jta.SaTransactionManagerFactory.SaGetTransactionManager",
            "com.sun.jts.jta.TransactionManagerImpl.getTransactionManagerImpl",
            "com.inprise.visitransact.jta.TransactionManagerImpl.getTransactionManagerImpl",
    };

    protected TransactionManager transactionManager;

    public TransactionalJtaTransactionPolicy() {
        transactionManager = lookupTransactionManager();
    }

    protected void runWithTransaction(final Runnable runnable, final boolean isNew) throws Throwable {
        if (isNew) {
            begin();
        }
        try {
            runnable.run();
        } catch (Throwable e) {
            rollback(isNew);
            throw e;
        }
        if (isNew) {
            commit();
        }
    }

    // todo: see @openjpa:openjpa-kernel/src/main/java/org/apache/openjpa/ee/AutomaticManagedRuntime.java
    private TransactionManager lookupTransactionManager() {
        TransactionManager tm;
        for (String jndiName : TRANSACTION_MANAGER_JNDI_NAMES) {
            try {
                tm = InitialContext.doLookup(jndiName);
                LOG.debug("JTA TransactionManager found at JNDI location [{}]", jndiName);

                return tm;
            } catch (NamingException ex) {
                LOG.debug("No JTA TransactionManager found at JNDI location [{}]", jndiName, ex);
            }
        }
        var loaders = Stream.of(Thread.currentThread().getContextClassLoader(), getClass().getClassLoader())
                .filter(Objects::nonNull)
                .distinct()
                .collect(toList());
        for (String method : METHODS) {
            final int sep = method.lastIndexOf('.');
            try {
                Class<?> clazz = null;
                for (final var loader : loaders) {
                    try {
                        clazz = loader.loadClass(method.substring(0, sep));
                    } catch (final NoClassDefFoundError | ClassNotFoundException cnfe) {
                        // continue
                    }
                }
                if (clazz != null) {
                    final var getter = clazz.getDeclaredMethod(method.substring(sep + 1));
                    getter.setAccessible(true);
                    final var txMgr = (TransactionManager) getter.invoke(null);
                    if (txMgr != null) {
                        return txMgr;
                    }
                }
            } catch (final RuntimeException | ReflectiveOperationException | NoClassDefFoundError t) {
                // no-op
            }
        }
        LOG.warn("Could not find the transaction manager through any of following locations: {}",
                String.join(",", TRANSACTION_MANAGER_JNDI_NAMES));
        return null;
    }

    private void begin() throws Exception {
        transactionManager.begin();
    }

    private void commit() throws Exception {
        try {
            transactionManager.commit();
        } catch (HeuristicMixedException | HeuristicRollbackException | RollbackException | SystemException e) {
            throw new CamelException("Unable to commit transaction", e);
        } catch (Exception | Error e) {
            rollback(true);
            throw e;
        }
    }

    protected void rollback(boolean isNew) throws Exception {
        try {
            if (isNew) {
                transactionManager.rollback();
            } else {
                transactionManager.setRollbackOnly();
            }
        } catch (Throwable e) {
            LOG.warn("Could not rollback transaction!", e);
        }
    }

    protected Transaction suspendTransaction() throws Exception {
        return transactionManager.suspend();
    }

    protected void resumeTransaction(final Transaction suspendedTransaction) {
        if (suspendedTransaction == null) {
            return;
        }

        try {
            transactionManager.resume(suspendedTransaction);
        } catch (Throwable e) {
            LOG.warn("Could not resume transaction!", e);
        }
    }

    protected boolean hasActiveTransaction() throws Exception {
        return transactionManager.getStatus() != Status.STATUS_MARKED_ROLLBACK
                && transactionManager.getStatus() != Status.STATUS_NO_TRANSACTION;
    }
}
