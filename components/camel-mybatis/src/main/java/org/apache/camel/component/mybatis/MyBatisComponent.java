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
package org.apache.camel.component.mybatis;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

/**
 * @version 
 */
public class MyBatisComponent extends UriEndpointComponent {

    @Metadata(label = "advanced")
    private SqlSessionFactory sqlSessionFactory;
    @Metadata(defaultValue = "SqlMapConfig.xml")
    private String configurationUri = "SqlMapConfig.xml";

    public MyBatisComponent() {
        super(MyBatisEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MyBatisEndpoint answer = new MyBatisEndpoint(uri, this, remaining);
        setProperties(answer, parameters);
        return answer;
    }

    protected SqlSessionFactory createSqlSessionFactory() throws IOException {
        ObjectHelper.notNull(configurationUri, "configurationUri", this);
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), configurationUri);
        try {
            return new SqlSessionFactoryBuilder().build(is);
        } finally {
            IOHelper.close(is);
        }
    }

    public SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }

    /**
     * To use the {@link SqlSessionFactory}
     */
    public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    public String getConfigurationUri() {
        return configurationUri;
    }

    /**
     * Location of MyBatis xml configuration file.
     * <p/>
     * The default value is: SqlMapConfig.xml loaded from the classpath
     */
    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (sqlSessionFactory == null) {
            sqlSessionFactory = createSqlSessionFactory();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }
}
