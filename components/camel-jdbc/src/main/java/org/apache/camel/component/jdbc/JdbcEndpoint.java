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

import javax.sql.DataSource;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version $Revision:520964 $
 */
public class JdbcEndpoint extends DefaultEndpoint<DefaultExchange> {
    private int readSize;
    private DataSource dataSource;

    public JdbcEndpoint(String endpointUri, Component component, DataSource dataSource) {
        super(endpointUri, component);
        this.dataSource = dataSource;
    }

    public boolean isSingleton() {
        return true;
    }

    public Consumer<DefaultExchange> createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Not supported");
    }

    public Producer<DefaultExchange> createProducer() throws Exception {
        return new JdbcProducer(this, dataSource, readSize);
    }

    public void setReadSize(int readSize) {
        this.readSize = readSize;
    }

}
