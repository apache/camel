/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.idempotent.jpa;

import org.apache.camel.processor.idempotent.MessageIdRepository;
import org.springframework.orm.jpa.JpaCallback;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.PersistenceException;

import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public class JpaMessageIdRepository implements MessageIdRepository {
    protected static final String QUERY_STRING = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1 and x.messageId = ?2";
    private JpaTemplate jpaTemplate;
    private String processorName;
	private TransactionTemplate transactionTemplate;

    public static JpaMessageIdRepository jpaMessageIdRepository(String persistenceUnit, String processorName) {
        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnit);
        return jpaMessageIdRepository(new JpaTemplate(entityManagerFactory), processorName);
    }

    public static JpaMessageIdRepository jpaMessageIdRepository(JpaTemplate jpaTemplate, String processorName) {
        return new JpaMessageIdRepository(jpaTemplate, processorName);
    }

    public JpaMessageIdRepository(JpaTemplate template, String processorName) {
        this(template, createTransactionTemplate(template), processorName);
    }

    public JpaMessageIdRepository(JpaTemplate template, TransactionTemplate transactionTemplate, String processorName) {
        this.jpaTemplate = template;
        this.processorName = processorName;
        this.transactionTemplate=transactionTemplate;
    }
    
    static private TransactionTemplate createTransactionTemplate(JpaTemplate jpaTemplate) {
    	TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new JpaTransactionManager(jpaTemplate.getEntityManagerFactory()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionTemplate;
    }

    public boolean contains(final String messageId) {
    	// Run this in single transaction.
    	Boolean rc = (Boolean) transactionTemplate.execute(new TransactionCallback(){
			public Object doInTransaction(TransactionStatus arg0) {
				
		        List list = jpaTemplate.find(QUERY_STRING, processorName, messageId);
		        if (list.isEmpty()) {
		            MessageProcessed processed = new MessageProcessed();
		            processed.setProcessorName(processorName);
		            processed.setMessageId(messageId);
		            jpaTemplate.persist(processed);
		            jpaTemplate.flush();
		            return Boolean.FALSE;
		        }
		        else {
		            return Boolean.TRUE;
		        }
			}
		});
    	return rc.booleanValue();
    }
}
