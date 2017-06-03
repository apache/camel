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
package org.apache.camel.component.ibatis;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.ibatis.sqlmap.client.SqlMapClient;
import com.ibatis.sqlmap.client.SqlMapClientBuilder;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ResourceHelper;

/**
 * An <a href="http://camel.apache.org/ibatis.html>iBatis Component</a>
 * for performing SQL operations using an XML mapping file to abstract away the SQL
 *
 * @version 
 * 
 * <pre>
 * Ibatis Component used to read/write to a database.
 *
 * <u>Requires one of the following:</u>
 *
 * 1. A Sql Map config file either on the root of
 * the classpath or explicitly set.
 *
 * <b>OR</b>
 *
 * 2. A SqlMapClient explicit set.
 *
 * Using Ibatis as a source of data (&lt;from&gt;) you can use this component
 * to treat a database table as a logical queue.
 * Details are available in the {@link IBatisConsumer}
 *
 * Using Ibatis as a destination for data (&lt;to&gt;) you can use this
 * component to run an insert statement either on a single message or if the
 * delivered content contains a collection of messages it can iterate through
 * the collection and run the insert on each element.
 * Details are available in the {@link IBatisProducer}
 * </pre>
 *
 * @see IBatisProducer
 * @see IBatisConsumer
 */
public class IBatisComponent extends UriEndpointComponent {
    private static final String DEFAULT_CONFIG_URI = "classpath:SqlMapConfig.xml";
    @Metadata(label = "advanced")
    private SqlMapClient sqlMapClient;
    @Metadata(defaultValue = DEFAULT_CONFIG_URI)
    private String sqlMapConfig = DEFAULT_CONFIG_URI;
    @Metadata(defaultValue = "true")
    private boolean useTransactions = true;

    public IBatisComponent() {
        super(IBatisEndpoint.class);
    }

    public IBatisComponent(SqlMapClient sqlMapClient) {
        this();
        this.sqlMapClient = sqlMapClient;
    }

    @Override
    protected IBatisEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        IBatisEndpoint answer = new IBatisEndpoint(uri, this, remaining);
        answer.setUseTransactions(isUseTransactions());
        setProperties(answer, parameters);
        return answer;
    }

    protected SqlMapClient createSqlMapClient() throws IOException {
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), sqlMapConfig);
        return SqlMapClientBuilder.buildSqlMapClient(is);
    }

    // Properties
    //-------------------------------------------------------------------------

    public SqlMapClient getSqlMapClient() {
        return sqlMapClient;
    }

    /**
     * To use the given {@link com.ibatis.sqlmap.client.SqlMapClient}
     */
    public void setSqlMapClient(SqlMapClient sqlMapClient) {
        this.sqlMapClient = sqlMapClient;
    }

    public String getSqlMapConfig() {
        return sqlMapConfig;
    }

    /**
     * Location of iBatis xml configuration file.
     * <p/>
     * The default value is: SqlMapConfig.xml loaded from the classpath
     */
    public void setSqlMapConfig(String sqlMapConfig) {
        this.sqlMapConfig = sqlMapConfig;
    }

    public boolean isUseTransactions() {
        return useTransactions;
    }

    /**
     * Whether to use transactions.
     * <p/>
     * This option is by default true.
     */
    public void setUseTransactions(boolean useTransactions) {
        this.useTransactions = useTransactions;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (sqlMapClient == null) {
            sqlMapClient = createSqlMapClient();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }
}
