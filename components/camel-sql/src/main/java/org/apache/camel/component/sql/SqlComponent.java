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
package org.apache.camel.component.sql;

import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultComponent;

/**
 * @version $Revision:520964 $
 */
public class SqlComponent extends DefaultComponent<Exchange> {
    private DataSource dataSource;

    public SqlComponent() {
    }

    public SqlComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint<Exchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        String dataSourceRef = getAndRemoveParameter(parameters, "dataSourceRef", String.class);
        if (dataSourceRef != null) {
            dataSource = getCamelContext().getRegistry().lookup(dataSourceRef, DataSource.class);
            if (dataSource == null) {
                throw new IllegalArgumentException("DataSource " + dataSourceRef + " not found in registry");
            }
        }
        
        return new SqlEndpoint(uri, remaining.replaceAll("#", "?"), this, dataSource, parameters);
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

}
