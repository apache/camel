package org.apache.camel.component.cxf.spring;

import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.BusWiringBeanFactoryPostProcessor;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.apache.cxf.version.Version;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class CxfSpringEndpointBean extends CxfEndpointBean implements ApplicationContextAware {
    private ApplicationContext applicationContext;
    
    public CxfSpringEndpointBean() {
        super();
    }
    
    public CxfSpringEndpointBean(ReflectionServiceFactoryBean factory) {
        super(factory);
    }
    
    @SuppressWarnings("deprecation")
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        applicationContext = ctx;
        if (bus == null) {
            if (Version.getCurrentVersion().startsWith("2.3")) {
                // Don't relate on the DefaultBus
                BusFactory factory = new SpringBusFactory(ctx);
                bus = factory.createBus();               
                BusWiringBeanFactoryPostProcessor.updateBusReferencesInContext(bus, ctx);
            } else {
                bus = BusWiringBeanFactoryPostProcessor.addDefaultBus(ctx);
            }
        }
    }
    
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
    
}