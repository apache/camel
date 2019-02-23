package org.apache.camel.component.jooq.beans;

import static org.apache.camel.component.jooq.db.Tables.BOOK_STORE;
import org.apache.camel.component.jooq.db.tables.records.BookStoreRecord;
import org.jooq.Query;
import org.jooq.ResultQuery;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component
public class BookStoreRecordBean {
    private String name = "test";

    public BookStoreRecord generate() {
        return new BookStoreRecord(name);
    }

    public ResultQuery select() {
        return DSL.selectFrom(BOOK_STORE).where(BOOK_STORE.NAME.eq(name));
    }

    public Query delete() {
        return DSL.delete(BOOK_STORE).where(BOOK_STORE.NAME.eq(name));
    }
}
