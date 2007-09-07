/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * An <a href="http://activemq.apache.org/camel/ibatis.html>iBatis Component</a>
 * for performing SQL operations using an XML mapping file to abstract away the SQL
 *
 * @version $Revision: 1.1 $
 */
public class IBatisComponent extends DefaultComponent {
    private static final transient Log LOG = LogFactory.getLog(IBatisComponent.class);

    public static final String DEFAULT_CONFIG_URI = "SqlMapConfig.xml";
    private SqlMapClient sqlMapClient;
    private Resource sqlMapResource;

    public IBatisComponent() {
    }

    public IBatisComponent(SqlMapClient sqlMapClient) {
        this.sqlMapClient = sqlMapClient;
    }

    // Properties
    //-------------------------------------------------------------------------
    public SqlMapClient getSqlMapClient() throws IOException {
        if (sqlMapClient == null) {
            sqlMapClient = createSqlMapClient();
        }
        return sqlMapClient;
    }

    public void setSqlMapClient(SqlMapClient sqlMapClient) {
        this.sqlMapClient = sqlMapClient;
    }

    public Resource getSqlMapResource() {
        if (sqlMapResource == null) {
            sqlMapResource = new ClassPathResource(DEFAULT_CONFIG_URI);
            LOG.debug("Defaulting to use the iBatis configuration from: " + sqlMapResource);
        }
        return sqlMapResource;
    }

    public void setSqlMapResource(Resource sqlMapResource) {
        this.sqlMapResource = sqlMapResource;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        return new IBatisEndpoint(uri, this, remaining);
    }

    protected SqlMapClient createSqlMapClient() throws IOException {
        InputStream in = getSqlMapResource().getInputStream();
        return SqlMapClientBuilder.buildSqlMapClient(in);
    }
}
