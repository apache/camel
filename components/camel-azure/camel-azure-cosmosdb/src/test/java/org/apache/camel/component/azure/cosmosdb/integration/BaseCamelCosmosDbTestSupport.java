package org.apache.camel.component.azure.cosmosdb.integration;

import com.azure.cosmos.CosmosAsyncClient;
import org.apache.camel.CamelContext;
import org.apache.camel.component.azure.cosmosdb.CosmosDbComponent;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.test.junit5.CamelTestSupport;

public abstract class BaseCamelCosmosDbTestSupport extends CamelTestSupport {

    protected CosmosAsyncClient client;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        client = CosmosDbTestUtils.createAsyncClient();

        final CamelContext context = super.createCamelContext();
        final CosmosDbComponent component = new CosmosDbComponent(context);

        component.init();
        component.getConfiguration().setCosmosAsyncClient(client);
        context.addComponent("azure-cosmosdb", component);

        return context;
    }
}
