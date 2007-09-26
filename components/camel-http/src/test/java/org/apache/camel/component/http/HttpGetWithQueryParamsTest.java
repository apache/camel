/**
 * 
 */
package org.apache.camel.component.http;


/**
 * TODO Provide description for HttpGetWithQueryParamsTest.
 * 
 * @author <a href="mailto:nsandhu@raleys.com">nsandhu</a>
 *
 */
public class HttpGetWithQueryParamsTest extends HttpGetTest{
    protected void setUp() throws Exception {
        super.setUp();
        expectedText = "activemq.apache.org";
    }
    
}
