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
package org.apache.camel.component.cassandra;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.EndpointHelper;

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
public class CassandraComponent extends DefaultComponent {
    /**
     * Regular expression for parsing host name
     */
    private static final String HOST_PATTERN = "[\\w.\\-]+";
    /**
     * Regular expression for parsing several hosts name
     */
    private static final String HOSTS_PATTERN = HOST_PATTERN + "(?:," + HOST_PATTERN + ")*";
    /**
     * Regular expression for parsing port
     */
    private static final String PORT_PATTERN = "\\d+";
    /**
     * Regular expression for parsing keyspace
     */
    private static final String KEYSPACE_PATTERN = "\\w+";
    /**
     * Regular expression for parsing URI host1,host2:9042/keyspace
     */
    private static final Pattern HOSTS_PORT_KEYSPACE_PATTERN = Pattern.compile(
            "^(" + HOSTS_PATTERN + ")?" // Hosts
                    + "(?::(" + PORT_PATTERN + "))?" // Port
                    + "(?:/(" + KEYSPACE_PATTERN + "))?$"); // Keyspace
    /**
     * Regular expression for parsing URI bean:sessionRef
     */
    private static final Pattern BEAN_REF_PATTERN = Pattern.compile(
            "^bean:([\\w.\\-]+)(?:/(" + KEYSPACE_PATTERN + "))?$"); // Keyspace

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Cluster cluster;
        Session session;
        String keyspace;
        // Try URI of type cql:bean:session or 
        Matcher beanRefMatcher = BEAN_REF_PATTERN.matcher(remaining);
        if (beanRefMatcher.matches()) {
            String beanRefName = beanRefMatcher.group(1);
            keyspace = beanRefMatcher.group(2);
            Object bean = EndpointHelper.resolveParameter(getCamelContext(), "#" + beanRefName, Object.class);
            if (bean instanceof Session) {
                session = (Session) bean;
                cluster = session.getCluster();
                keyspace = session.getLoggedKeyspace();
            } else if (bean instanceof Cluster) {
                cluster = (Cluster) bean;
                session = null;
            } else {
                throw new IllegalArgumentException("CQL Bean type should be of type Session or Cluster but was " + bean);
            }
        } else {
            // Try URI of type cql:host1,host2:9042/keyspace
            cluster = clusterBuilder(remaining, parameters).build();
            session = null;
            keyspace = getAndRemoveParameter(parameters, "keyspace", String.class);
        }

        Endpoint endpoint = new CassandraEndpoint(uri, this, cluster, session, keyspace);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Parse URI of the form cql://host1,host2:9042/keyspace and create a
     * {@link Cluster.Builder}
     */
    protected Cluster.Builder clusterBuilder(String remaining, Map<String, Object> parameters) throws NumberFormatException {
        Cluster.Builder clusterBuilder = Cluster.builder();
        Matcher matcher = HOSTS_PORT_KEYSPACE_PATTERN.matcher(remaining);
        if (matcher.matches()) {
            // Parse hosts
            String hostsGroup = matcher.group(1);
            if (hostsGroup != null && !hostsGroup.isEmpty()) {
                String[] hosts = hostsGroup.split(",");
                clusterBuilder = clusterBuilder.addContactPoints(hosts);
            }
            // Parse port
            String portGroup = matcher.group(2);
            if (portGroup != null) {
                Integer port = Integer.valueOf(portGroup);
                clusterBuilder = clusterBuilder.withPort(port);
            }
            // Parse keyspace
            String keyspaceGroup = matcher.group(3);
            if (keyspaceGroup != null && !keyspaceGroup.isEmpty()) {
                String keyspace = keyspaceGroup;
                parameters.put("keyspace", keyspace);
            }
        } else {
            throw new IllegalArgumentException("Invalid CQL URI");
        }
        // Cluster name parameter
        String clusterName = getAndRemoveParameter(parameters, "clusterName", String.class);
        if (clusterName != null) {
            clusterBuilder = clusterBuilder.withClusterName(clusterName);
        }
        // Username and password
        String username = getAndRemoveOrResolveReferenceParameter(parameters, "username", String.class);
        String password = getAndRemoveOrResolveReferenceParameter(parameters, "password", String.class);
        if (username != null && !username.isEmpty() && password != null) {
            clusterBuilder.withCredentials(username, password);
        }
        return clusterBuilder;
    }
}