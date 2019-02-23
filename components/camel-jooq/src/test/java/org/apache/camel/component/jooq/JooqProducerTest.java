package org.apache.camel.component.jooq;

import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.component.jooq.db.Tables.BOOK_STORE;
import org.apache.camel.component.jooq.db.tables.records.BookStoreRecord;
import org.jooq.Query;
import org.jooq.Result;
import org.jooq.ResultQuery;
import org.junit.Assert;
import org.junit.Test;

public class JooqProducerTest extends BaseJooqTest {

    @Test
    public void testCRUD() {
        ProducerTemplate producerTemplate = context.createProducerTemplate();

        // Insert and select
        BookStoreRecord bookStoreRecord = new BookStoreRecord("test");
        producerTemplate.sendBody(context.getEndpoint("direct:insert"), ExchangePattern.InOut, bookStoreRecord);
        ResultQuery querySelect = create.selectFrom(BOOK_STORE).where(BOOK_STORE.NAME.eq("test"));
        Result actual = (Result)producerTemplate.sendBody(context.getEndpoint("direct:select"), ExchangePattern.InOut, querySelect);
        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(bookStoreRecord, actual.get(0));

        // Update and select
        String newName = "testNew";
        Query query = create.update(BOOK_STORE).set(BOOK_STORE.NAME, newName).where(BOOK_STORE.NAME.eq("test"));
        producerTemplate.sendBody(context.getEndpoint("direct:update"), ExchangePattern.InOut, query);
        querySelect = create.selectFrom(BOOK_STORE).where(BOOK_STORE.NAME.eq(newName));
        actual = (Result)producerTemplate.sendBody(context.getEndpoint("direct:select"), ExchangePattern.InOut, querySelect);
        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(newName, ((BookStoreRecord)actual.get(0)).getName());

        // Delete and select
        query = create.delete(BOOK_STORE).where(BOOK_STORE.NAME.eq(newName));
        producerTemplate.sendBody(context.getEndpoint("direct:delete"), ExchangePattern.InOut, query);
        querySelect = create.selectFrom(BOOK_STORE).where(BOOK_STORE.NAME.eq(newName));
        actual = (Result)producerTemplate.sendBody(context.getEndpoint("direct:select"), ExchangePattern.InOut, querySelect);
        Assert.assertEquals(0, actual.size());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        JooqComponent jooqComponent = (JooqComponent) context().getComponent("jooq");
        JooqConfiguration jooqConfiguration = new JooqConfiguration();
        jooqConfiguration.setDatabaseConfiguration(create.configuration());
        jooqComponent.setConfiguration(jooqConfiguration);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:insert")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord");

                from("direct:update")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord/execute");

                from("direct:delete")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord/execute");

                from("direct:select")
                        .to("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord/fetch");
            }
        };
    }

}
