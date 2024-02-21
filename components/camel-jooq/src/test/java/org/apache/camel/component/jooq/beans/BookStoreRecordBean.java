/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jooq.beans;

import org.apache.camel.component.jooq.db.tables.records.BookStoreRecord;
import org.jooq.Query;
import org.jooq.ResultQuery;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import static org.apache.camel.component.jooq.db.Tables.BOOK_STORE;

@Component
public class BookStoreRecordBean {
    private String name = "test";

    public BookStoreRecord generate() {
        return new BookStoreRecord(name);
    }

    public ResultQuery<BookStoreRecord> select() {
        return DSL.selectFrom(BOOK_STORE).where(BOOK_STORE.NAME.eq(name));
    }

    public Query delete() {
        return DSL.delete(BOOK_STORE).where(BOOK_STORE.NAME.eq(name));
    }
}
