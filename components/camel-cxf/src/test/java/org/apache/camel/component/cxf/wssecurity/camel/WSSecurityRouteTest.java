package org.apache.camel.component.cxf.wssecurity.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.wssecurity.client.Client;
import org.apache.camel.component.cxf.wssecurity.server.CxfServer;
import org.apache.camel.hello_world_soap_http.Greeter;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class WSSecurityRouteTest extends CamelTestSupport {
    protected CxfServer cxfServer;
    protected AbstractXmlApplicationContext applicationContext;

    @Before
    public void setUp() throws Exception {       
        //start the back end service
        int port = CXFTestSupport.getPort1();
        cxfServer = new CxfServer(port);
        applicationContext = createApplicationContext();
        super.setUp();
    }
    
    @After
    public void shutdownService() {
        if (cxfServer != null) {
            cxfServer.stop();
        }
        if (applicationContext != null) {
            applicationContext.stop();
        }
    }
    

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext);
    }


    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/wssecurity/camel/camel-context.xml");
    }
    
    protected String getRouterAddress() {
        return "http://localhost:" + CXFTestSupport.getPort2() + "/WSSecurityRouteTest/GreeterPort";
    }
    
    @Test
    public void testInvokeService() throws Exception {
        Client client = new Client(getRouterAddress());
        Greeter greeter = client.getClient();
        assertEquals("Get a wrong response", "Hello Security", greeter.greetMe("Security"));
    }
   
 
}
