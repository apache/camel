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
package org.apache.camel.component.elsql;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.sql.DataSource;

import com.opengamma.elsql.ElSql;
import com.opengamma.elsql.ElSqlConfig;
import com.opengamma.elsql.SpringSqlParams;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.sql.DefaultSqlEndpoint;
import org.apache.camel.component.sql.DefaultSqlPrepareStatementStrategy;
import org.apache.camel.component.sql.SqlPrepareStatementStrategy;
import org.apache.camel.component.sql.SqlProcessingStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Use ElSql to define SQL queries. Extends the SQL Component.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "elsql", title = "ElSQL", syntax = "elsql:elsqlName:resourceUri",
             category = { Category.DATABASE, Category.SQL })
public class ElsqlEndpoint extends DefaultSqlEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ElsqlEndpoint.class);

    private ElSql elSql;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    @UriPath
    @Metadata(required = true)
    private final String elsqlName;
    @UriPath
    private String resourceUri;
    @UriParam
    private DataSource dataSource;
    @UriParam
    private ElSqlDatabaseVendor databaseVendor;
    @UriParam(label = "advanced")
    private ElSqlConfig elSqlConfig;

    public ElsqlEndpoint(final String uri, final Component component, final NamedParameterJdbcTemplate namedJdbcTemplate,
                         final DataSource dataSource,
                         final String elsqlName, final String resourceUri) {
        super(uri, component, null);
        this.elsqlName = elsqlName;
        this.resourceUri = resourceUri;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        final SqlProcessingStrategy proStrategy = new ElsqlSqlProcessingStrategy(elSql);
        final SqlPrepareStatementStrategy preStategy = new ElsqlSqlPrepareStatementStrategy();

        final Exchange dummy = createExchange();
        final SqlParameterSource param = new ElsqlSqlMapSource(dummy, null);
        final String sql = elSql.getSql(elsqlName, new SpringSqlParams(param));
        LOG.debug("ElsqlConsumer @{} using sql: {}", elsqlName, sql);

        final ElsqlConsumer consumer
                = new ElsqlConsumer(this, processor, namedJdbcTemplate, sql, param, preStategy, proStrategy);
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
        final SqlPrepareStatementStrategy prepareStrategy = getPrepareStatementStrategy() != null
                ? getPrepareStatementStrategy() : new DefaultSqlPrepareStatementStrategy(getSeparator());
        final ElsqlProducer result
                = new ElsqlProducer(this, elSql, elsqlName, namedJdbcTemplate, dataSource, prepareStrategy, isBatch());
        return result;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        org.apache.camel.util.ObjectHelper.notNull(resourceUri, "resourceUri", this);

        if (elSqlConfig == null && databaseVendor != null) {
            elSqlConfig = databaseVendor.asElSqlConfig();
        } else if (elSqlConfig == null) {
            elSqlConfig = ElSqlDatabaseVendor.Default.asElSqlConfig();
        }

        // load and parse the sources which are from classpath
        parseResources(resourceUri, uri -> ResourceHelper.isClasspathUri(uri));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // load and parse the sources which are not from classpath
        parseResources(resourceUri, uri -> !ResourceHelper.isClasspathUri(uri));
    }

    private void parseResources(String resourceUri, Predicate<String> predicate) throws Exception {
        // there can be multiple resources
        // so we have all this lovely code to turn that into an URL[]
        final List<URL> list = new ArrayList<>();
        final Iterable<String> it = ObjectHelper.createIterable(resourceUri);

        for (final String path : it) {
            if (predicate.test(path)) {
                final URL url = ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), path);
                list.add(url);
            }
        }

        if (list.size() > 0) {
            final URL[] urls = list.toArray(new URL[list.size()]);
            elSql = ElSql.parse(elSqlConfig, urls);
        }
    }

    /**
     * The name of the elsql to use (is @NAMED in the elsql file)
     */
    public String getElsqlName() {
        return elsqlName;
    }

    public ElSqlDatabaseVendor getDatabaseVendor() {
        return databaseVendor;
    }

    /**
     * To use a vendor specific {@link com.opengamma.elsql.ElSqlConfig}
     */
    public void setDatabaseVendor(final ElSqlDatabaseVendor databaseVendor) {
        this.databaseVendor = databaseVendor;
    }

    public ElSqlConfig getElSqlConfig() {
        return elSqlConfig;
    }

    /**
     * To use a specific configured ElSqlConfig. It may be better to use the <tt>databaseVendor</tt> option instead.
     */
    public void setElSqlConfig(final ElSqlConfig elSqlConfig) {
        this.elSqlConfig = elSqlConfig;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * The resource file which contains the elsql SQL statements to use. You can specify multiple resources separated by
     * comma. The resources are loaded on the classpath by default, you can prefix with <tt>file:</tt> to load from file
     * system. Notice you can set this option on the component and then you do not have to configure this on the
     * endpoint.
     */
    public void setResourceUri(final String resourceUri) {
        this.resourceUri = resourceUri;
    }

    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets the DataSource to use to communicate with the database.
     */
    @Override
    public void setDataSource(final DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
