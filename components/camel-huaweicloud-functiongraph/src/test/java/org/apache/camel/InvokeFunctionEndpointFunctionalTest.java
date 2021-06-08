package org.apache.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.constants.FunctionGraphProperties;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvokeFunctionEndpointFunctionalTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(InvokeFunctionEndpointFunctionalTest.class.getName());

    private static final String AUTHENTICATION_KEY = "replace_this_with_authentication_key";
    private static final String SECRET_KEY = "replace_this_with_secret_key";
    private static final String FUNCTION_NAME = "replace_this_with_function_name";
    private static final String FUNCTION_PACKAGE = "replace_this_with_function_package";
    private static final String PROJECT_ID = "replace_this_with_project_id";
    private static final String ENDPOINT = "replace_this_with_endpoint";

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:invoke_function")
                        .setProperty(FunctionGraphProperties.XCFFLOGTYPE, constant("tail"))
                        .to("hwcloud-functiongraph:invokeFunction?" +
                                "authenticationKey=" + AUTHENTICATION_KEY +
                                "&secretKey=" + SECRET_KEY +
                                "&functionName=" + FUNCTION_NAME +
                                "&functionPackage=" + FUNCTION_PACKAGE +
                                "&projectId=" + PROJECT_ID +
                                "&endpoint=" + ENDPOINT +
                                "&ignoreSslVerification=true")
                        .log("Invoke function successful")
                        .to("log:LOG?showAll=true")
                        .to("mock:invoke_function_result");
            }
        };
    }

    /**
     * The following test cases should be manually enabled to perform test against the actual HuaweiCloud
     * FunctionGraph server with real user credentials. To perform this test, manually comment out the @Ignore
     * annotation and enter relevant service parameters in the placeholders above (static variables of this test class)
     *
     * @throws Exception
     */
    @Ignore("Manually enable this once you configure the parameters in the placeholders above")
    @Test
    public void testInvokeFunction() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:invoke_function_result");
        mock.expectedMinimumMessageCount(1);
        String sampleBody = "replace_with_your_body";
        template.sendBody("direct:invoke_function", sampleBody);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertNotNull(responseExchange.getProperty(FunctionGraphProperties.XCFFLOGS));
        assertTrue(responseExchange.getProperty(FunctionGraphProperties.XCFFLOGS).toString().length() > 0);
    }
}
