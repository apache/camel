package org.apache.camel;

import org.apache.camel.component.workday.WorkdayComponent;
import org.apache.camel.component.workday.WorkdayConfiguration;
import org.apache.camel.component.workday.WorkdayEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class WorkdayComponentTest extends CamelTestSupport {

    @Test
    public void createProducerMinimalConfiguration() throws Exception {

        WorkdayComponent workdayComponent = context.getComponent("workday", WorkdayComponent.class);

        WorkdayEndpoint workdayEndpoint = (WorkdayEndpoint)workdayComponent.createEndpoint("workday-raas:/<Owner>/<ReportName>?" +
        "host=impl.workday.com" +
                "&tenant=camel" +
                "&clientId=f7014d38-99d2-4969-b740-b5b62db6b46a" +
                "&clientSecret=7dbaf280-3cea-11ea-b77f-2e728ce88125" +
                "&tokenRefresh=88689ab63cda" +
                "&format=json");

        WorkdayConfiguration workdayConfiguration = workdayEndpoint.getWorkdayConfiguration();

        assertEquals(workdayConfiguration.getHost(),"impl.workday.com");
        assertEquals(workdayConfiguration.getTenant(),"camel");
        assertEquals(workdayConfiguration.getClientId(),"f7014d38-99d2-4969-b740-b5b62db6b46a");
        assertEquals(workdayConfiguration.getClientSecret(),"7dbaf280-3cea-11ea-b77f-2e728ce88125");
        assertEquals(workdayConfiguration.getTokenRefresh(),"88689ab63cda");
    }

    @Test
    public void createProducerNoHostConfiguration() throws Exception {

        WorkdayComponent workdayComponent = context.getComponent("workday", WorkdayComponent.class);

        try {

            WorkdayEndpoint workdayEndpoint = (WorkdayEndpoint) workdayComponent
                    .createEndpoint("workday-raas:/<Owner>/<ReportName>?" +
                    "tenant=camel" +
                    "&clientId=f7014d38-99d2-4969-b740-b5b62db6b46a" +
                    "&clientSecret=7dbaf280-3cea-11ea-b77f-2e728ce88125" +
                    "&tokenRefresh=88689ab63cda" +
                    "&format=json");
        } catch (ResolveEndpointFailedException exception) {

            assertEquals(exception.getMessage(),"Failed to resolve endpoint: " +
                    "The parameters host, tenant, clientId, clientSecret and tokenRefresh are required.");
            return;
        }

        assertTrue("Required parameters validation failed.", false);
    }

    @Test
    public void createProducerUrlValidation() throws Exception {

        WorkdayComponent workdayComponent = context.getComponent("workday", WorkdayComponent.class);

        WorkdayEndpoint workdayEndpoint = (WorkdayEndpoint) workdayComponent
                .createEndpoint("workday-raas:/ISU_Camel/Custom_Report_Employees?" +
                    "host=camel.myworkday.com" +
                    "&tenant=camel" +
                    "&clientId=f7014d38-99d2-4969-b740-b5b62db6b46a" +
                    "&clientSecret=7dbaf280-3cea-11ea-b77f-2e728ce88125" +
                    "&tokenRefresh=88689ab63cda" +
                    "&param=test1");

        String workdayUri = workdayEndpoint.getUri();

        assertEquals(workdayUri,"https://camel.myworkday.com/ccx/service/customreport2/camel/ISU_Camel/Custom_Report_Employees?param=test1&format=json");
    }
}
