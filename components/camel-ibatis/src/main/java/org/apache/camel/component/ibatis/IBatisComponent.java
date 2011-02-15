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
import java.io.InputStreamReader;
import java.util.Map;

import com.ibatis.sqlmap.client.SqlMapClient;
import com.ibatis.sqlmap.client.SqlMapClientBuilder;
import org.apache.camel.component.ResourceBasedComponent;
import org.springframework.core.io.Resource;

/**
 * An <a href="http://camel.apache.org/ibatis.html>iBatis Component</a>
 * for performing SQL operations using an XML mapping file to abstract away the SQL
 *
 * @version $Revision$
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
public class IBatisComponent extends ResourceBasedComponent {
    private static final String DEFAULT_CONFIG_URI = "classpath:SqlMapConfig.xml";
    private SqlMapClient sqlMapClient;
    private String sqlMapConfig = DEFAULT_CONFIG_URI;
    private boolean useTransactions = true;

    public IBatisComponent() {
    }

    public IBatisComponent(SqlMapClient sqlMapClient) {
        this.sqlMapClient = sqlMapClient;
    }

    /**
     * Creates an IbatisEndpoint for use by an IbatisConsumer or IbatisProducer.
     */
    @Override
    protected IBatisEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        IBatisEndpoint answer = new IBatisEndpoint(uri, this, remaining);
        setProperties(answer, parameters);
        return answer;
    }

    private SqlMapClient createSqlMapClient() throws IOException {
        Resource resource = resolveMandatoryResource(sqlMapConfig);
        InputStream is = resource.getInputStream();
        return SqlMapClientBuilder.buildSqlMapClient(new InputStreamReader(is));
    }

    // Properties
    //-------------------------------------------------------------------------

    /**
     * Returns the configured SqlMapClient.
     *
     * @return com.ibatis.sqlmap.client.SqlMapClient
     * @throws IOException If configured with a SqlMapConfig and there
     * is a problem reading the resource.
     */
    public SqlMapClient getSqlMapClient() throws IOException {
        if (sqlMapClient == null) {
            sqlMapClient = createSqlMapClient();
        }
        return sqlMapClient;
    }
    
    /**
     * Sets the SqlMapClient
     */
    public void setSqlMapClient(SqlMapClient sqlMapClient) {
        this.sqlMapClient = sqlMapClient;
    }

    /**
     * The Spring uri of the SqlMapConfig
     */
    public String getSqlMapConfig() {
        return sqlMapConfig;
    }

    public void setSqlMapConfig(String sqlMapConfig) {
        this.sqlMapConfig = sqlMapConfig;
    }

    public boolean isUseTransactions() {
        return useTransactions;
    }
    
    public void setUseTransactions(boolean useTransactions) {
        this.useTransactions = useTransactions;
    }
}
