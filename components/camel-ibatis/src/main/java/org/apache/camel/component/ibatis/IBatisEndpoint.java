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
import java.sql.SQLException;
import java.util.List;

import com.ibatis.sqlmap.client.SqlMapClient;

import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;

/**
 * An <a href="http://activemq.apache.org/camel/ibatis.html>iBatis Endpoint</a>
 * for performing SQL operations using an XML mapping file to abstract away the SQL
 *
 * @version $Revision$
 */
public class IBatisEndpoint extends DefaultPollingEndpoint {
    private final String entityName;

    public IBatisEndpoint(String endpointUri, IBatisComponent component, String entityName) {
        super(endpointUri, component);
        this.entityName = entityName;
    }

    public IBatisEndpoint(String endpointUri, String entityName) {
        super(endpointUri);
        this.entityName = entityName;
    }

    @Override
    public IBatisComponent getComponent() {
        return (IBatisComponent) super.getComponent();
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer createProducer() throws Exception {
        return new IBatisProducer(this);
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        return new IBatisPollingConsumer(this);
    }

    /**
     * Returns the iBatis SQL client
     */
    public SqlMapClient getSqlClient() throws IOException {
        return getComponent().getSqlMapClient();
    }

    public String getEntityName() {
        return entityName;
    }

    public void query(Message message) throws IOException, SQLException {
        String name = getEntityName();
        List list = getSqlClient().queryForList(name);
        message.setBody(list);
        message.setHeader("org.apache.camel.ibatis.queryName", name);

    }
}
