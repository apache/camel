package org.apache.camel.component.cxf.spring;

import org.apache.cxf.frontend.AbstractEndpointFactory;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;

public class CxfEndpointBean extends AbstractEndpointFactory {    
    public CxfEndpointBean() {
        setServiceFactory(new ReflectionServiceFactoryBean());
    }    
}
