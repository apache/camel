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
package org.apache.camel.osgi;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.core.osgi.OsgiCamelContextPublisher;
import org.apache.camel.core.osgi.OsgiEventAdminNotifier;
import org.apache.camel.spring.SpringCamelContext;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.osgi.context.BundleContextAware;

@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelContextFactoryBean extends org.apache.camel.spring.CamelContextFactoryBean implements BundleContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(CamelContextFactoryBean.class);
    
    @XmlTransient
    private BundleContext bundleContext;

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        LOG.debug("Using BundleContext: {}", bundleContext);
        this.bundleContext = bundleContext;
    }
    
    protected SpringCamelContext createContext() {
        SpringCamelContext ctx = newCamelContext();
        // only set the name if its explicit (Camel will auto assign name if none explicit set)
        if (!isImplicitId()) {
            ctx.setName(getId());
        }
        return ctx;
    }
    
    protected SpringCamelContext newCamelContext() {
        return new OsgiSpringCamelContext(getApplicationContext(), getBundleContext());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        getContext().getManagementStrategy().addEventNotifier(new OsgiCamelContextPublisher(bundleContext));
        try {
            getClass().getClassLoader().loadClass("org.osgi.service.event.EventAdmin");
            getContext().getManagementStrategy().addEventNotifier(new OsgiEventAdminNotifier(bundleContext));
        } catch (Throwable t) {
            // Ignore, if the EventAdmin package is not available, just don't use it
            LOG.debug("EventAdmin package is not available, just don't use it");
        }
    }
}
