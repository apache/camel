package org.apache.camel.cdi;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.store.Item;
import org.apache.camel.cdi.store.ShoppingBean;
import org.apache.camel.component.cdi.CdiBeanRegistry;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.webbeans.cditest.CdiTestContainer;
import org.apache.webbeans.cditest.CdiTestContainerLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class CdiContainerBeanManagerTest extends CamelTestSupport {

    private MockEndpoint resultEndpoint;

    private ProducerTemplate template;

    CdiTestContainer cdiContainer;
    CamelContext camelContext;

    @Inject
    ShoppingBean shoppingBean;

    @Before
    public void setUp() throws Exception {
        cdiContainer = CdiTestContainerLoader.getCdiContainer();
        cdiContainer.bootContainer();

        System.out.println(">> Container started and bean manager instantiated !");

        // Camel
        context = new DefaultCamelContext(new CdiBeanRegistry());
        context.addRoutes(createRouteBuilder());
        context.setTracing(true);
        context.start();
        resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        template = context.createProducerTemplate();

        System.out.println(">> Camel started !");
    }

    @After
    public void shutDown() throws Exception {
        cdiContainer.shutdownContainer();
        context.stop();
    }

    @Test
    public void testInjection() throws InterruptedException {
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("direct:inject", "hello");

        assertMockEndpointsSatisfied();

        Exchange exchange = resultEndpoint.getExchanges().get(0);
        List<Item> results = (List<Item>) exchange.getIn().getBody();

        Object[] items = (Object[]) results.toArray();
        Object[] itemsExpected = (Object[]) itemsExpected().toArray();
        for(int i=0; i< items.length; ++i) {
           Item itemExpected = (Item)items[i];
           Item itemReceived = (Item)itemsExpected[i];
           assertEquals(itemExpected.getName(), itemReceived.getName());
           assertEquals(itemExpected.getPrice(), itemReceived.getPrice());
        }

        assertNotNull(results);
    }

    private ArrayList<Item> itemsExpected() {
        ArrayList<Item> products = new ArrayList<Item>();
        Item defaultItem = new Item();
        defaultItem.setName("Default Item");
        defaultItem.setPrice(1000L);

        for (int i = 1; i < 10; i++) {
            Item item = new Item("Item-" + i, i * 1500L);
            products.add(item);
        }

        return products;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:inject")
                        .beanRef("shoppingBean", "listAllProducts")
                        .to("mock:result");

            }

        };
    }


}
