package org.apache.camel.itest.greeter;


import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@ContextConfiguration
public class JmsToCxfInOutTest extends AbstractJUnit4SpringContextTests {
    private static int port = AvailablePortFinder.getNextAvailable(20025);
    static {
        //set them as system properties so Spring can use the property place holder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("JmsToCxfInOutTest.port", Integer.toString(port));
    }
    
    @Autowired
    protected ProducerTemplate template;

    @Test
    public void testJmsToCxfInOut() throws Exception {
        assertNotNull(template);
        
        String out = template.requestBodyAndHeader("jms:queue:bridge.cxf", "Willem", CxfConstants.OPERATION_NAME, "greetMe", String.class);
        assertEquals("Hello Willem", out);

        // call for the other opertion
        out = template.requestBodyAndHeader("jms:queue:bridge.cxf", new Object[0], CxfConstants.OPERATION_NAME, "sayHi", String.class);
        assertEquals("Bonjour", out);
    }

}
