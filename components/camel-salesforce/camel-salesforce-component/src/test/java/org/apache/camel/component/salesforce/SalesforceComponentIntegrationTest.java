package org.apache.camel.component.salesforce;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SalesforceComponentIntegrationTest extends AbstractSalesforceTestBase {

    private SalesforceHttpClient client = new SalesforceHttpClient();
    private SalesforceComponent component;

    @Test
    public void usesUserSuppliedHttpClient() {
        assertEquals(client, component.getHttpClient());
    }

    @Override
    protected void createComponent() throws Exception {
        super.createComponent();
        client = new SalesforceHttpClient();
        SalesforceEndpointConfig config = new SalesforceEndpointConfig();
        config.setHttpClient(client);
        component = (SalesforceComponent) context.getComponent("salesforce");
        component.setConfig(config);
        component.getLoginConfig().setLazyLogin(true);
    }
}
