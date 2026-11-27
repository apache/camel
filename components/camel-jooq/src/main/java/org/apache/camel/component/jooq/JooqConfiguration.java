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
package org.apache.camel.component.jooq;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.jooq.Configuration;

@UriParams
public class JooqConfiguration implements Cloneable {

    @UriPath(label = "common",
             description = "The fully qualified name of the jOOQ generated record class (an org.jooq.UpdatableRecord) of the"
                           + " table. The consumer reads the rows of its table, and the producer stores records of this class"
                           + " for the NONE operation. The operation can follow after a slash, for example"
                           + " jooq:com.foo.BookRecord/fetch.")
    private Class<?> entityType;

    @UriParam(label = "producer",
              description = "The operation that the producer performs. NONE stores the record of the entityType in the"
                            + " message body (an insert or an update), EXECUTE runs a query, and FETCH runs a select query and"
                            + " sets its org.jooq.Result as the message body.",
              defaultValue = "NONE",
              enums = "NONE,EXECUTE,FETCH")
    private JooqOperation operation = JooqOperation.NONE;

    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether the consumer deletes a row from the table after its exchange is processed successfully."
                            + " A row whose exchange failed or was marked rollback only is not deleted and is consumed again"
                            + " in the next poll. If false, every poll consumes all the rows of the table again.")
    private boolean consumeDelete = true;

    @UriParam(label = "producer",
              description = "A plain SQL statement that the producer runs for the EXECUTE and FETCH operations. When set, the"
                            + " message body is not read. The NONE operation does not use it.")
    private String query;

    @UriParam(label = "common",
              description = "The org.jooq.Configuration that the consumer and the producer use to run their queries. It holds"
                            + " the connection to the database (a connection provider or a data source) and the SQL dialect.")
    private Configuration databaseConfiguration;

    public JooqConfiguration() {
    }

    public JooqOperation getOperation() {
        return operation;
    }

    public void setOperation(JooqOperation operation) {
        this.operation = operation;
    }

    public Configuration getDatabaseConfiguration() {
        return databaseConfiguration;
    }

    public void setDatabaseConfiguration(Configuration databaseConfiguration) {
        this.databaseConfiguration = databaseConfiguration;
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public void setEntityType(Class<?> entityType) {
        this.entityType = entityType;
    }

    public boolean isConsumeDelete() {
        return consumeDelete;
    }

    public void setConsumeDelete(boolean consumeDelete) {
        this.consumeDelete = consumeDelete;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public JooqConfiguration copy() {
        try {
            return (JooqConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
