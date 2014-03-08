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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.Component;
import org.apache.camel.blueprint.BlueprintCamelContext;
import org.apache.camel.component.cxf.blueprint.BlueprintSupport;
import org.apache.camel.component.cxf.blueprint.RsClientBlueprintBean;
import org.apache.camel.component.cxf.blueprint.RsServerBlueprintBean;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;

public class CxfRsBlueprintEndpoint extends CxfRsEndpoint {
    private AbstractJAXRSFactoryBean bean;
    private BlueprintContainer blueprintContainer;
    private BundleContext bundleContext;
    private BlueprintCamelContext blueprintCamelContext;
    
    @Deprecated 
    /**
     * It will be removed in Camel 3.0
     * @param comp
     * @param bean
     */
    public CxfRsBlueprintEndpoint(Component comp, AbstractJAXRSFactoryBean bean) {
        super(bean.getAddress(), comp);
        this.bean = bean;
        BlueprintSupport support = (BlueprintSupport)bean;
        setBlueprintContainer(support.getBlueprintContainer());
        setBundleContext(support.getBundleContext());
    }

    public CxfRsBlueprintEndpoint(Component comp, String uri, AbstractJAXRSFactoryBean bean) {
        super(uri, comp);
        this.bean = bean;
        setAddress(bean.getAddress());
        // update the sfb address by resolving the properties
        bean.setAddress(getAddress());
        BlueprintSupport support = (BlueprintSupport)bean;
        setBlueprintContainer(support.getBlueprintContainer());
        setBundleContext(support.getBundleContext());
    }
    
    public BlueprintContainer getBlueprintContainer() {
        return blueprintContainer;
    }

    public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public BlueprintCamelContext getBlueprintCamelContext() {
        return blueprintCamelContext;
    }

    public void setBlueprintCamelContext(BlueprintCamelContext blueprintCamelContext) {
        this.blueprintCamelContext = blueprintCamelContext;
    }
    
    @Override
    protected JAXRSServerFactoryBean newJAXRSServerFactoryBean() {
        checkBeanType(bean, JAXRSServerFactoryBean.class);
        return (RsServerBlueprintBean)bean;
    }
    
    @Override
    protected JAXRSClientFactoryBean newJAXRSClientFactoryBean() {
        checkBeanType(bean, JAXRSClientFactoryBean.class);
        return (RsClientBlueprintBean)bean;
    }
    

}
