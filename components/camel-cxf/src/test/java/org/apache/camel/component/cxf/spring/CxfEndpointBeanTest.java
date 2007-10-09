package org.apache.camel.component.cxf.spring;

import junit.framework.TestCase;
import org.apache.cxf.configuration.spring.ConfigurerImpl;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfEndpointBeanTest extends TestCase{
    
    public void testCxfEndpointBeanDefinitionParser() {
        ClassPathXmlApplicationContext ctx = 
            new ClassPathXmlApplicationContext(new String[]{"org/apache/camel/component/cxf/spring/CxfEndpointBeans.xml"});
        
        CxfEndpointBean routerEndpoint = (CxfEndpointBean)ctx.getBean("routerEndpoint");
        assertEquals("Got the wrong endpoint address", routerEndpoint.getAddress(), "http://localhost:9000/router");
        assertEquals("Got the wrong endpont service class", routerEndpoint.getServiceClass().getCanonicalName(), "org.apache.camel.component.cxf.HelloService");
    }
    
}
