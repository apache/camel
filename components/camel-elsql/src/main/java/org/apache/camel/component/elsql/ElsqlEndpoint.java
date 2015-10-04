/**
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
package org.apache.camel.component.elsql;

import java.net.URL;

import com.opengamma.elsql.ElSql;
import com.opengamma.elsql.ElSqlConfig;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.sql.SqlEndpoint;
import org.apache.camel.component.sql.SqlPrepareStatementStrategy;
import org.apache.camel.component.sql.SqlProcessingStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.springframework.jdbc.core.JdbcTemplate;

@UriEndpoint(scheme = "elsql", title = "SQL", syntax = "elsql:elsqlName/resourceUri", consumerClass = ElsqlConsumer.class, label = "database,sql")
public class ElsqlEndpoint extends SqlEndpoint {

    private volatile ElSql elSql;

    @UriPath
    @Metadata(required = "true")
    private String elsqlName;
    @UriPath
    private String resourceUri;
    @UriParam
    private ElSqlConfig elSqlConfig;

    public ElsqlEndpoint(String uri, Component component, JdbcTemplate jdbcTemplate, String elsqlName, String resourceUri) {
        super(uri, component, jdbcTemplate, null);
        this.elsqlName = elsqlName;
        this.resourceUri = resourceUri;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SqlProcessingStrategy proStrategy = new ElsqlSqlProcessingStrategy(elsqlName, elSql);
        SqlPrepareStatementStrategy preStategy = new ElsqlSqlPrepareStatementStrategy();

        ElsqlConsumer consumer = new ElsqlConsumer(this, processor, getJdbcTemplate(), elsqlName, preStategy, proStrategy);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setOnConsume(getOnConsume());
        consumer.setOnConsumeFailed(getOnConsumeFailed());
        consumer.setOnConsumeBatchComplete(getOnConsumeBatchComplete());
        consumer.setBreakBatchOnConsumeFail(isBreakBatchOnConsumeFail());
        consumer.setExpectedUpdateCount(getExpectedUpdateCount());
        consumer.setUseIterator(isUseIterator());
        consumer.setRouteEmptyResultSet(isRouteEmptyResultSet());
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        ElsqlProducer result = new ElsqlProducer(this, elSql, elsqlName, getJdbcTemplate());
        return result;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(elSqlConfig, "elSqlConfig", this);
        ObjectHelper.notNull(resourceUri, "resourceUri", this);

        URL url = ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), resourceUri);

        elSql = ElSql.parse(elSqlConfig, url);
    }

    /**
     * The name of the elsql to use (is @NAMED in the elsql file)
     */
    public String getElsqlName() {
        return elsqlName;
    }

    public ElSqlConfig getElSqlConfig() {
        return elSqlConfig;
    }

    /**
     * The elsql configuration to use
     */
    public void setElSqlConfig(ElSqlConfig elSqlConfig) {
        this.elSqlConfig = elSqlConfig;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * The eqlsql resource tile which contains the elsql SQL statements to use
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }
}
