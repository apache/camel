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
package org.apache.camel.spring;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.core.xml.AbstractCamelRedeliveryPolicyFactoryBean;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spring.util.CamelContextResolverHelper;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Configuration of redelivery policy.
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "redeliveryPolicyProfile")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelRedeliveryPolicyFactoryBean extends AbstractCamelRedeliveryPolicyFactoryBean implements FactoryBean<RedeliveryPolicy>, ApplicationContextAware {

    @XmlTransient
    private ApplicationContext applicationContext;

    @Override
    protected CamelContext getCamelContextWithId(String camelContextId) {
        return CamelContextResolverHelper.getCamelContextWithId(applicationContext, camelContextId);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
