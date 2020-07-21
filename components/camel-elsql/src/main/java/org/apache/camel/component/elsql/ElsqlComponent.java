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

import java.util.Map;

import javax.sql.DataSource;

import com.opengamma.elsql.ElSqlConfig;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.PropertiesHelper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Component("elsql")
public class ElsqlComponent extends DefaultComponent {

    @Metadata
    private ElSqlDatabaseVendor databaseVendor;
    @Metadata
    private DataSource dataSource;
    @Metadata(label = "advanced")
    private ElSqlConfig elSqlConfig;
    @Metadata
    private String resourceUri;

    public ElsqlComponent() {
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
            target = getDataSource();
        }
        if (target == null) {
            throw new IllegalArgumentException("DataSource must be configured");
        }

        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(target);
        Map<String, Object> params = PropertiesHelper.extractProperties(parameters, "template.");
        PropertyBindingSupport.bindProperties(getCamelContext(), jdbcTemplate, params);

        String elsqlName = remaining;
        String resUri = resourceUri;
        String[] part = remaining.split(":");
        if (part.length == 2) {
            elsqlName = part[0];
            resUri = part[1];
        } else if (part.length > 2) {
            throw new IllegalArgumentException("Invalid uri. Must by elsql:elsqlName:resourceUri, was: " + uri);
        }

        String onConsume = getAndRemoveParameter(parameters, "consumer.onConsume", String.class);
        if (onConsume == null) {
            onConsume = getAndRemoveParameter(parameters, "onConsume", String.class);
        }
        String onConsumeFailed = getAndRemoveParameter(parameters, "consumer.onConsumeFailed", String.class);
        if (onConsumeFailed == null) {
            onConsumeFailed = getAndRemoveParameter(parameters, "onConsumeFailed", String.class);
        }
        String onConsumeBatchComplete = getAndRemoveParameter(parameters, "consumer.onConsumeBatchComplete", String.class);
        if (onConsumeBatchComplete == null) {
            onConsumeBatchComplete = getAndRemoveParameter(parameters, "onConsumeBatchComplete", String.class);
        }

        ElsqlEndpoint endpoint = new ElsqlEndpoint(uri, this, jdbcTemplate, target, elsqlName, resUri);
        endpoint.setElSqlConfig(elSqlConfig);
        endpoint.setDatabaseVendor(databaseVendor);
        endpoint.setDataSource(target);
        endpoint.setDataSourceRef(dataSourceRef);
        endpoint.setOnConsume(onConsume);
        endpoint.setOnConsumeFailed(onConsumeFailed);
        endpoint.setOnConsumeBatchComplete(onConsumeBatchComplete);
        return endpoint;
    }

    public ElSqlDatabaseVendor getDatabaseVendor() {
        return databaseVendor;
    }

    /**
     * To use a vendor specific {@link com.opengamma.elsql.ElSqlConfig}
     */
    public void setDatabaseVendor(ElSqlDatabaseVendor databaseVendor) {
        this.databaseVendor = databaseVendor;
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
     * Sets the DataSource to use to communicate with the database.
     */
    public ElSqlConfig getElSqlConfig() {
        return elSqlConfig;
    }

    /**
     * To use a specific configured ElSqlConfig. It may be better to use the <tt>databaseVendor</tt> option instead.
     */
    public void setElSqlConfig(ElSqlConfig elSqlConfig) {
        this.elSqlConfig = elSqlConfig;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * The resource file which contains the elsql SQL statements to use. You can specify multiple resources separated by comma.
     * The resources are loaded on the classpath by default, you can prefix with <tt>file:</tt> to load from file system.
     * Notice you can set this option on the component and then you do not have to configure this on the endpoint.
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

}
