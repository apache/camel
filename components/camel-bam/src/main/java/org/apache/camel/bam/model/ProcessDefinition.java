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
package org.apache.camel.bam.model;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Table;

import org.apache.camel.bam.EntityManagerCallback;
import org.apache.camel.bam.EntityManagerTemplate;
import org.apache.camel.bam.QueryUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
@Entity
@Table(name = "CAMEL_PROCESSDEFINITION")
public class ProcessDefinition extends EntitySupport {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinition.class);
    private String name;

    @Column(unique = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static ProcessDefinition getRefreshedProcessDefinition(EntityManagerTemplate entityManagerTemplate, ProcessDefinition definition) {
        // TODO refresh doesn't tend to work - maybe its a spring thing?
        // template.refresh(definition);

        ObjectHelper.notNull(definition, "definition");
        final Long id = definition.getId();
        if (id == null) {
            LOG.warn("No primary key is available!");
            return findOrCreateProcessDefinition(entityManagerTemplate, definition.getName());
        }
        return entityManagerTemplate.execute(new EntityManagerCallback<ProcessDefinition>() {
            @Override
            public ProcessDefinition execute(EntityManager entityManager) {
                return entityManager.find(ProcessDefinition.class, id);
            }
        });
    }

    public static ProcessDefinition findOrCreateProcessDefinition(EntityManagerTemplate entityManagerTemplate, final String processName) {
        final String definitionsQuery = "select x from " + QueryUtils.getTypeName(ProcessDefinition.class)
                + " x where x.name = :processName";
        List<ProcessDefinition> list = entityManagerTemplate.execute(new EntityManagerCallback<List<ProcessDefinition>>() {
            @Override
            public List<ProcessDefinition> execute(EntityManager entityManager) {
                return entityManager.createQuery(definitionsQuery, ProcessDefinition.class).
                        setParameter("processName", processName).
                        getResultList();
            }
        });
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
