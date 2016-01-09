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
package org.apache.camel.component.sql.stored;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.sql.stored.template.TemplateStoredProcedureFactory;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

@UriEndpoint(scheme = "sql-stored", title = "SQL StoredProcedure", syntax = "sql-stored:template", producerOnly = true, label = "database,sql")
public class SqlStoredEndpoint extends DefaultEndpoint {

    private JdbcTemplate jdbcTemplate;
    private TemplateStoredProcedureFactory templateStoredProcedureFactory;

    @UriPath @Metadata(required = "true")
    private String template;

    public SqlStoredEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(template, "template");
        ObjectHelper.notNull(templateStoredProcedureFactory, "templateStoredProcedureFactory");

        return new SqlStoredProducer(this, template, templateStoredProcedureFactory);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("This component does not support consumer");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    protected String createEndpointUri() {
        return "sql-stored:" + UnsafeUriCharactersEncoder.encode(template);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(templateStoredProcedureFactory);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(templateStoredProcedureFactory);
    }

    public String getTemplate() {
        return template;
    }

    /**
     * The stored procedure template to perform
     */
    public void setTemplate(String template) {
        this.template = template;
    }

    public TemplateStoredProcedureFactory getTemplateStoredProcedureFactory() {
        return templateStoredProcedureFactory;
    }

    /**
     * To use a custom instance of TemplateStoredProcedureFactory
     */
    public void setTemplateStoredProcedureFactory(TemplateStoredProcedureFactory templateStoredProcedureFactory) {
        this.templateStoredProcedureFactory = templateStoredProcedureFactory;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    /**
     * to use a custom instance of JdbcTemplate
     */
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
}
