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
package org.apache.camel.component.cxf;

import javax.xml.namespace.QName;

import org.apache.cxf.service.Service;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.wsdl11.WSDLServiceFactory;

public class WSDLServiceFactoryBean extends ReflectionServiceFactoryBean {
    private QName serviceName;
    private QName endpointName;

    @Override
    public Service create() {

        WSDLServiceFactory factory = new WSDLServiceFactory(getBus(), getWsdlURL(), getServiceQName());

        setService(factory.create());
        initializeDefaultInterceptors();
        //disable the date interceptors
        updateEndpointInfors();
        createEndpoints();

        return getService();
    }


    private void updateEndpointInfors() {
        Service service = getService();

        for (ServiceInfo inf : service.getServiceInfos()) {
            for (EndpointInfo ei : inf.getEndpoints()) {
                //setup the endpoint address
                ei.setAddress("local://" + ei.getService().getName().toString() + "/" + ei.getName().getLocalPart());
                // working as the dispatch mode, the binding factory will not add interceptor
                //ei.getBinding().setProperty(AbstractBindingFactory.DATABINDING_DISABLED, Boolean.TRUE);
            }
        }

    }
    
    protected void checkServiceClassAnnotations(Class<?> sc) {
        // do nothing here
    }

    public void setServiceName(QName name) {
        serviceName = name;
    }

    public String getServiceName() {
        return serviceName.toString();
    }

    public QName getServiceQName() {
        return serviceName;
    }

    public QName getEndpointName() {
        // get the endpoint name if it is not set
        if (endpointName == null) {
            endpointName = getService().getEndpoints().keySet().iterator().next();
        }
        return endpointName;
    }

    public void setEndpointName(QName name) {
        endpointName = name;
    }
}
