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
import org.springframework.orm.jpa.JpaTemplate;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public class JpaMessageIdRepository implements MessageIdRepository {
    protected static final String QUERY_STRING = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1 and x.messageId = ?2";
    private JpaTemplate template;
    private String processorName;

    public static JpaMessageIdRepository jpaMessageIdRepository(String persistenceUnit, String processorName) {
        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnit);
        return jpaMessageIdRepository(new JpaTemplate(entityManagerFactory), processorName);
    }

    public static JpaMessageIdRepository jpaMessageIdRepository(JpaTemplate jpaTemplate, String processorName) {
        return new JpaMessageIdRepository(jpaTemplate, processorName);
    }

    public JpaMessageIdRepository(JpaTemplate template, String processorName) {
        this.template = template;
        this.processorName = processorName;
    }

    public boolean contains(String messageId) {
        List list = template.find(QUERY_STRING, processorName, messageId);
        if (list.isEmpty()) {
            MessageProcessed processed = new MessageProcessed();
            processed.setProcessorName(processorName);
            processed.setMessageId(messageId);
            template.persist(processed);
            template.flush();
            return false;
        }
        else {
            return true;
        }
    }
}
