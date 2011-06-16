package org.apache.camel.component.cxf.spring;

import java.util.List;

import org.apache.camel.component.cxf.jaxrs.BeanIdAware;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.BusWiringBeanFactoryPostProcessor;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.JAXRSServiceFactoryBean;
import org.apache.cxf.version.Version;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class SpringJAXRSServerFactoryBean extends JAXRSServerFactoryBean implements
    ApplicationContextAware, BeanIdAware {
    private String beanId;
    
    public SpringJAXRSServerFactoryBean() {
        super();
    }

    public SpringJAXRSServerFactoryBean(JAXRSServiceFactoryBean sf) {
        super(sf);
    }

    @SuppressWarnings("deprecation")
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        if (bus == null) {
            if (Version.getCurrentVersion().startsWith("2.3")) {
                // Don't relate on the DefaultBus
                BusFactory factory = new SpringBusFactory(ctx);
                bus = factory.createBus();               
                setBus(bus);
                BusWiringBeanFactoryPostProcessor.updateBusReferencesInContext(bus, ctx);
            } else {
                setBus(BusWiringBeanFactoryPostProcessor.addDefaultBus(ctx));
            }
        }
    }

    public String getBeanId() {
        return beanId;
    }

    public void setBeanId(String id) {
        beanId = id;
    }
    
    // to walk round the issue of setting the serviceClass in CXF
    public void setServiceClass(Class clazz) {
        setResourceClasses(clazz);
    }
    
    // add this mothod for testing
    List<String> getSchemaLocations() {
        return schemaLocations;
    }
}