package org.apache.camel.http.common;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.apache.camel.spi.SendDynamicAware.DynamicAwareEntry;

public class HttpSendDynamicAwareTest {

    private HttpSendDynamicAware httpSendDynamicAware;
    @Before
    public void setUp() throws Exception {
        this.httpSendDynamicAware = new HttpSendDynamicAware();
        
    }

    @Test
    public void testHttpUndefinedPortWithPathParseUri() {
        this.httpSendDynamicAware.setScheme("http");
        DynamicAwareEntry entry = new DynamicAwareEntry("http://localhost/test", null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not add port if http and not specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttpsUndefinedPortParseUri() {
        this.httpSendDynamicAware.setScheme("https");
        DynamicAwareEntry entry = new DynamicAwareEntry("https://localhost/test", null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not add port if https and not specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttp4UndefinedPortWithPathParseUri() {
        this.httpSendDynamicAware.setScheme("http4");
        DynamicAwareEntry entry = new DynamicAwareEntry("http4://localhost/test", null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not add port if http4 and not specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttps4UndefinedPortParseUri() {
        this.httpSendDynamicAware.setScheme("https4");
        DynamicAwareEntry entry = new DynamicAwareEntry("https4://localhost/test", null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not add port if https4 and not specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttpPort80ParseUri() {
        this.httpSendDynamicAware.setScheme("http");
        DynamicAwareEntry entry = new DynamicAwareEntry("http://localhost:80/test", null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not port if http and port 80 specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttpsPort443ParseUri() {
        this.httpSendDynamicAware.setScheme("https");
        DynamicAwareEntry entry = new DynamicAwareEntry("https://localhost:443/test", null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should not port if https and port 443 specified", "localhost", result[0]);
    }
    
    @Test
    public void testHttpPort8080ParseUri() {
        this.httpSendDynamicAware.setScheme("http");
        DynamicAwareEntry entry = new DynamicAwareEntry("http://localhost:8080/test", null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should add port if http and port other than 80 specified", "localhost:8080", result[0]);
    }
    
    @Test
    public void testHttpsPort8443ParseUri() {
        this.httpSendDynamicAware.setScheme("https");
        DynamicAwareEntry entry = new DynamicAwareEntry("https://localhost:8443/test", null, null);
        String[] result = httpSendDynamicAware.parseUri(entry);
        assertEquals("Parse should add port if https and port other than 443 specified", "localhost:8443", result[0]);
    }

}
