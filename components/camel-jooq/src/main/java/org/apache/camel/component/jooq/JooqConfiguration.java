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

    @UriPath(label = "common", description = "JOOQ entity class")
    private Class<?> entityType;

    @UriParam(label = "producer", description = "Type of operation to execute on query", defaultValue = "NONE")
    private JooqOperation operation = JooqOperation.NONE;

    @UriParam(label = "consumer", defaultValue = "true", description = "Delete entity after it is consumed")
    private boolean consumeDelete = true;

    @UriParam(label = "producer", description = "To execute plain SQL query")
    private String query;

    @UriParam(label = "common", description = "To use a specific database configuration")
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
            return (JooqConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
