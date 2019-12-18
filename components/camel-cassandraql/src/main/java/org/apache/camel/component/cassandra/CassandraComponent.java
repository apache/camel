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
package org.apache.camel.component.cassandra;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.StringHelper;

/**
 * Represents the component that manages {@link CassandraEndpoint}. This
 * component is based on Datastax Java Driver for Cassandra.
 * <p/>
 * URI examples:
 * <ul>
 * <li>cql:localhost/keyspace</li>
 * <li>cql:host1,host2/keyspace</li>
 * <li>cql:host1:host2:9042/keyspace</li>
 * <li>cql:host1:host2</li>
 * <li>cql:bean:sessionRef</li>
 * <li>cql:bean:clusterRef/keyspace</li>
 * </ul>
 */
@Component("cql")
public class CassandraComponent extends DefaultComponent {

    public CassandraComponent() {
        this(null);
    }

    public CassandraComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String beanRef = null;
        String hosts = null;
        String port = null;
        String keyspace = null;

        int pos = remaining.lastIndexOf("/");
        if (pos > 0) {
            keyspace = remaining.substring(pos + 1);
            remaining = remaining.substring(0, pos);
        }

        // if its a bean reference to either a cluster/session then lookup
        if (remaining.startsWith("bean:")) {
            beanRef = remaining.substring(5);
        } else {
            // hosts and port (port is optional)
            if (remaining.contains(":")) {
                port = StringHelper.after(remaining, ":");
                hosts = StringHelper.before(remaining, ":");
            } else {
                hosts = remaining;
            }
        }

        ResultSetConversionStrategy rs = null;
        String strategy = getAndRemoveParameter(parameters, "resultSetConversionStrategy", String.class);
        if (strategy != null) {
            rs = ResultSetConversionStrategies.fromName(strategy);
        }
        CassandraEndpoint endpoint = new CassandraEndpoint(uri, this);
        endpoint.setBean(beanRef);
        endpoint.setHosts(hosts);
        if (port != null) {
            int num = CamelContextHelper.parseInteger(getCamelContext(), port);
            endpoint.setPort(num);
        }
        endpoint.setKeyspace(keyspace);
        if (rs != null) {
            endpoint.setResultSetConversionStrategy(rs);
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }

}
