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
package org.apache.camel.spring;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.management.InstrumentationAgentImpl;
import org.apache.camel.spi.InstrumentationAgent;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

@XmlRootElement(name = "jmxAgent")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelInstrumentationAgentFactoryBean
        implements FactoryBean, InitializingBean, DisposableBean {

    @XmlTransient
    private InstrumentationAgent jmxAgent;

    public Object getObject() throws Exception {
        return getJmxAgent();
    }

    public Class getObjectType() {
        return InstrumentationAgent.class;
    }

    public boolean isSingleton() {
        return true;
    }

    public InstrumentationAgent getJmxAgent() {
        if (jmxAgent == null) {
            jmxAgent = new InstrumentationAgentImpl();
        }
        return jmxAgent;
    }

    public void afterPropertiesSet() throws Exception {
        getJmxAgent().start();
    }

    public void destroy() throws Exception {
        getJmxAgent().stop();
    }
}