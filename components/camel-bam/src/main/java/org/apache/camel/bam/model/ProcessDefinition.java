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
package org.apache.camel.bam.model;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.orm.jpa.JpaTemplate;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
@Entity
public class ProcessDefinition extends EntitySupport {
    private static final transient Log log = LogFactory.getLog(ProcessDefinition.class);
    private String name;

    // This crap is required to work around a bug in hibernate
    @Override
    @Id
    @GeneratedValue
    public Long getId() {
        return super.getId();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static ProcessDefinition getRefreshedProcessDefinition(JpaTemplate template, ProcessDefinition definition) {
        // TODO refresh doesn't tend to work - maybe its a spring thing?
        //template.refresh(definition);

        ObjectHelper.notNull(definition, "definition");
        Long id = definition.getId();
        if (id == null) {
            log.warn("No primary key is available!");
            return findOrCreateProcessDefinition(template, definition.getName());
        }
        definition = template.find(ProcessDefinition.class, id);
        return definition;
    }

    public static ProcessDefinition findOrCreateProcessDefinition(JpaTemplate template, String processName) {
        List<ProcessDefinition> list = template.find("select x from " + ProcessDefinition.class.getName() + " x where x.name = ?1", processName);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        else {
            ProcessDefinition answer = new ProcessDefinition();
            answer.setName(processName);
            template.persist(answer);
            return answer;
        }
    }
}
