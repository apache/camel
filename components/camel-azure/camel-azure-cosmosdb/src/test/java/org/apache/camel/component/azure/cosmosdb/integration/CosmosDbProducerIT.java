package org.apache.camel.component.azure.cosmosdb.integration;

import java.util.List;
import java.util.stream.Collectors;

import com.azure.cosmos.models.CosmosDatabaseProperties;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CosmosDbProducerIT extends BaseCamelCosmosDbTestSupport {
    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;
    private String resultName = "mock:result";

    @AfterEach
    void removeAllDatabases() {
        // delete all databases being used in the test after each test
        client.readAllDatabases()
                .toIterable()
                .forEach(cosmosDatabaseProperties -> client.getDatabase(cosmosDatabaseProperties.getId()).delete()
                        .block());
    }

    @Test
    void testListDatabases() throws InterruptedException {

        // create bunch of databases
        final String prefixDatabaseNames = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final int expectedSize = 5;

        for (int i = 0; i < expectedSize; i++) {
            client.createDatabase(prefixDatabaseNames + i).block();
        }

        result.expectedMessageCount(1);

        template.send("direct:listDatabases", exchange -> {
        });

        result.assertIsSatisfied(1000);

        // check the names of the databases
        final List<CosmosDatabaseProperties> returnedDatabases = result.getExchanges().get(0).getMessage().getBody(List.class);

        final List<String> returnedDatabasesAsString = returnedDatabases
                .stream().map(CosmosDatabaseProperties::getId)
                .collect(Collectors.toList());

        assertEquals(5, returnedDatabasesAsString.size());

        for (int i = 0; i < expectedSize; i++) {
            assertTrue(returnedDatabasesAsString.contains(prefixDatabaseNames + i));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:listDatabases").to("azure-cosmosdb://?operation=listDatabases").to(resultName);
            }
        };
    }
}
