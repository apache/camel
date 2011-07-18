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
package org.apache.camel.component.jdbc;

import java.util.Map;
import javax.sql.DataSource;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * @version 
 */
public class JdbcEndpoint extends DefaultEndpoint {
    private int readSize;
    private boolean transacted;
    private DataSource dataSource;
    private Map<String, Object> parameters;
    private boolean useJDBC4ColumnNameAndLabelSemantics = true;

    public JdbcEndpoint() {
    }

    public JdbcEndpoint(String endpointUri, Component component, DataSource dataSource) {
        super(endpointUri, component);
        this.dataSource = dataSource;
    }

    public boolean isSingleton() {
        return true;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Not supported");
    }

    public Producer createProducer() throws Exception {
        return new JdbcProducer(this, dataSource, readSize,  parameters);
    }

    public int getReadSize() {
        return readSize;
    }

    public void setReadSize(int readSize) {
        this.readSize = readSize;
    }

    public boolean isTransacted() {
        return transacted;
    }

    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Optional parameters to the {@link java.sql.Statement}.
     * <p/>
     * For example to set maxRows, fetchSize etc.
     * 
     * @param parameters parameters which will be set using reflection
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public boolean isUseJDBC4ColumnNameAndLabelSemantics() {
        return useJDBC4ColumnNameAndLabelSemantics;
    }

    /**
     * Sets whether to use JDBC 4 or JDBC 3.0 or older semantic when retrieving column name.
     * <p/>
     * JDBC 4.0 uses columnLabel to get the column name where as JDBC 3.0 uses both columnName or columnLabel.
     * Unfortunately JDBC drivers behave differently so you can use this option to work out issues around your
     * JDBC driver if you get problem using this component
     * <p/>
     * This option is default <tt>true</tt>.
     *
     * @param useJDBC4ColumnNameAndLabelSemantics  <tt>true</tt> to use JDBC 4.0 semantics, <tt>false</tt> to use JDBC 3.0.
     */
    public void setUseJDBC4ColumnNameAndLabelSemantics(boolean useJDBC4ColumnNameAndLabelSemantics) {
        this.useJDBC4ColumnNameAndLabelSemantics = useJDBC4ColumnNameAndLabelSemantics;
    }

    @Override
    protected String createEndpointUri() {
        return "jdbc";
    }
}
