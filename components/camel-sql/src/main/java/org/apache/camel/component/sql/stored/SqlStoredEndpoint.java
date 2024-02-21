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
package org.apache.camel.component.sql.stored;

import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.sql.stored.template.TemplateParser;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Perform SQL queries as a JDBC Stored Procedures using Spring JDBC.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "sql-stored", title = "SQL Stored Procedure", syntax = "sql-stored:template",
             producerOnly = true, category = { Category.DATABASE }, headersClass = SqlStoredConstants.class)
public class SqlStoredEndpoint extends DefaultEndpoint {

    private CallableStatementWrapperFactory wrapperFactory;
    private JdbcTemplate jdbcTemplate;

    @UriPath(description = "Sets the stored procedure template to perform. You can externalize the template by using file: or classpath: as prefix and specify the location of the file.")
    @Metadata(required = true, supportFileReference = true, largeInput = true, inputLanguage = "sql")
    private String template;
    @UriParam(description = "Sets the DataSource to use to communicate with the database.")
    private DataSource dataSource;
    @UriParam(description = "Enables or disables batch mode")
    private boolean batch;
    @UriParam(description = "Whether to use the message body as the stored procedure template and then headers for parameters. If this option is enabled then the template in the uri is not used.")
    private boolean useMessageBodyForTemplate;
    @UriParam(description = "If set, will ignore the results of the stored procedure template and use the existing IN message as the OUT message for the continuation of processing")
    private boolean noop;
    @UriParam(description = "Store the template result in a header instead of the message body. By default, outputHeader == null and the template result is stored"
                            + " in the message body, any existing content in the message body is discarded. If outputHeader is set, the value is used as the name of the header"
                            + " to store the template result and the original message body is preserved.")
    private String outputHeader;
    @UriParam(description = "Whether this call is for a function.")
    private boolean function;
    @UriParam(label = "advanced", prefix = "template.", multiValue = true,
              description = "Configures the Spring JdbcTemplate with the key/values from the Map")
    private Map<String, Object> templateOptions;

    public SqlStoredEndpoint(String uri, SqlStoredComponent component) {
        super(uri, component);
    }

    public SqlStoredEndpoint(String uri, SqlStoredComponent component, JdbcTemplate jdbcTemplate) {
        super(uri, component);
        setJdbcTemplate(jdbcTemplate);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SqlStoredProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    @Override
    protected String createEndpointUri() {
        // Make sure it's properly encoded
        return "sql-stored:" + UnsafeUriCharactersEncoder.encode(this.template);
    }

    protected CallableStatementWrapperFactory getWrapperFactory() {
        return wrapperFactory;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        this.wrapperFactory = new CallableStatementWrapperFactory(
                jdbcTemplate, new TemplateParser(getCamelContext().getClassResolver()), isFunction());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (this.wrapperFactory != null) {
            this.wrapperFactory.shutdown();
        }
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isBatch() {
        return batch;
    }

    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    public boolean isUseMessageBodyForTemplate() {
        return useMessageBodyForTemplate;
    }

    public void setUseMessageBodyForTemplate(boolean useMessageBodyForTemplate) {
        this.useMessageBodyForTemplate = useMessageBodyForTemplate;
    }

    public boolean isNoop() {
        return noop;
    }

    public void setNoop(boolean noop) {
        this.noop = noop;
    }

    public String getOutputHeader() {
        return outputHeader;
    }

    public void setOutputHeader(String outputHeader) {
        this.outputHeader = outputHeader;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isFunction() {
        return function;
    }

    public void setFunction(boolean function) {
        this.function = function;
    }

    public Map<String, Object> getTemplateOptions() {
        return templateOptions;
    }

    public void setTemplateOptions(Map<String, Object> templateOptions) {
        this.templateOptions = templateOptions;
    }
}
