package org.apache.camel.component.hashicorp.vault.integration.operations;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.hashicorp.vault.services.HashicorpServiceFactory;
import org.apache.camel.test.infra.hashicorp.vault.services.HashicorpVaultService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;

public class HashicorpVaultBase extends CamelTestSupport {
    @RegisterExtension
    public static HashicorpVaultService service = HashicorpServiceFactory.createService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        return context;
    }
}
