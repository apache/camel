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
package org.apache.camel.component.sql;

import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The <a href="http://camel.apache.org/sql-component.html">SQL Component</a> is for working with databases using JDBC queries.
 */
@Component("sql")
public class SqlComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(SqlComponent.class);

    @Metadata
    private DataSource dataSource;
    @Metadata(label = "advanced", defaultValue = "true")
    private boolean usePlaceholder = true;

    public SqlComponent() {
    }

    public SqlComponent(Class<? extends Endpoint> endpointClass) {
    }

    public SqlComponent(CamelContext context) {
        super(context);
    }

    public SqlComponent(CamelContext context, Class<? extends Endpoint> endpointClass) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DataSource target = null;

        // endpoint options overrule component configured datasource
        DataSource ds = resolveAndRemoveReferenceParameter(parameters, "dataSource", DataSource.class);
        if (ds != null) {
            target = ds;
        }
        String dataSourceRef = getAndRemoveParameter(parameters, "dataSourceRef", String.class);
        if (target == null && dataSourceRef != null) {
            target = CamelContextHelper.mandatoryLookup(getCamelContext(), dataSourceRef, DataSource.class);
        }
        if (target == null) {
            // fallback and use component
            target = dataSource;
        }
        if (target == null) {
            // check if the registry contains a single instance of DataSource
            Set<DataSource> dataSources = getCamelContext().getRegistry().findByType(DataSource.class);
            if (dataSources.size() > 1) {
                throw new IllegalArgumentException("Multiple DataSources found in the registry and no explicit configuration provided");
            } else if (dataSources.size() == 1) {
                target = dataSources.stream().findFirst().orElse(null);
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("DataSource must be configured");
        }
        LOG.debug("Using default DataSource discovered from registry: {}", target);

        String parameterPlaceholderSubstitute = getAndRemoveParameter(parameters, "placeholder", String.class, "#");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(target);
        Map<String, Object> templateOptions = PropertiesHelper.extractProperties(parameters, "template.");
        PropertyBindingSupport.bindProperties(getCamelContext(), jdbcTemplate, templateOptions);

        String query = remaining;
        if (usePlaceholder) {
            query = query.replaceAll(parameterPlaceholderSubstitute, "?");
        }

        String onConsume = getAndRemoveParameter(parameters, "consumer.onConsume", String.class);
        if (onConsume == null) {
            onConsume = getAndRemoveParameter(parameters, "onConsume", String.class);
        }
        if (onConsume != null && usePlaceholder) {
            onConsume = onConsume.replaceAll(parameterPlaceholderSubstitute, "?");
        }
        String onConsumeFailed = getAndRemoveParameter(parameters, "consumer.onConsumeFailed", String.class);
        if (onConsumeFailed == null) {
            onConsumeFailed = getAndRemoveParameter(parameters, "onConsumeFailed", String.class);
        }
        if (onConsumeFailed != null && usePlaceholder) {
            onConsumeFailed = onConsumeFailed.replaceAll(parameterPlaceholderSubstitute, "?");
        }
        String onConsumeBatchComplete = getAndRemoveParameter(parameters, "consumer.onConsumeBatchComplete", String.class);
        if (onConsumeBatchComplete == null) {
            onConsumeBatchComplete = getAndRemoveParameter(parameters, "onConsumeBatchComplete", String.class);
        }
        if (onConsumeBatchComplete != null && usePlaceholder) {
            onConsumeBatchComplete = onConsumeBatchComplete.replaceAll(parameterPlaceholderSubstitute, "?");
        }

        SqlEndpoint endpoint = new SqlEndpoint(uri, this, jdbcTemplate, query);
        endpoint.setPlaceholder(parameterPlaceholderSubstitute);
        endpoint.setUsePlaceholder(isUsePlaceholder());
        endpoint.setOnConsume(onConsume);
        endpoint.setOnConsumeFailed(onConsumeFailed);
        endpoint.setOnConsumeBatchComplete(onConsumeBatchComplete);
        endpoint.setDataSource(ds);
        endpoint.setDataSourceRef(dataSourceRef);
        endpoint.setTemplateOptions(templateOptions);
        return endpoint;
    }

    /**
     * Sets the DataSource to use to communicate with the database.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets whether to use placeholder and replace all placeholder characters with ? sign in the SQL queries.
     * <p/>
     * This option is default <tt>true</tt>
     */
    public void setUsePlaceholder(boolean usePlaceholder) {
        this.usePlaceholder = usePlaceholder;
    }

    public boolean isUsePlaceholder() {
        return usePlaceholder;
    }
}
