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
package org.apache.camel.component.neo4j;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.neo4j.driver.Driver;

@Configurer
@UriParams
public class Neo4jConfiguration implements Cloneable {

    @Metadata
    @UriParam(description = "Url for connecting to Neo database")
    private String databaseUrl;
    @UriParam(label = "security", description = "Basic authentication database user", displayName = "Database user",
              secret = true)
    private String username;
    @UriParam(label = "security", description = "Basic authentication database password", displayName = "Database password",
              secret = true)
    private String password;
    @UriParam(label = "security", description = "Basic authentication database realm", displayName = "Database realm",
              secret = true)
    private String realm;
    @UriParam(label = "security", description = "Bearer authentication database realm", displayName = "Realm", secret = true)
    private String token;
    @UriParam(label = "security", description = "Kerberos Authentication encoded base64 ticket",
              displayName = "Encoded base64 ticket",
              secret = true)
    private String kerberosAuthTicket;

    @UriParam
    private String query;
    @UriParam
    private String label;
    @UriParam
    private String vectorIndexName;
    @UriParam
    private String alias;
    @UriParam(defaultValue = "false")
    private boolean detachRelationship = false;
    @UriParam
    private Integer dimension;
    @UriParam(defaultValue = "cosine")
    private Neo4jSimilarityFunction similarityFunction = Neo4jSimilarityFunction.cosine;
    @UriParam(defaultValue = "0.0")
    private double minScore = 0.0;
    @UriParam(defaultValue = "3")
    private int maxResults = 3;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Driver driver;

    /**
     * URI of the Neo4j server - used for Authentication
     */
    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public void setDatabaseUrl(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    /**
     * User of the database - used for Basic Authentication
     */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Password for dbUser - used for Basic Authentication
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Node Label
     */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Node alias
     */
    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * Detach a relationship - set true if want to delete a node and detach its relationships to other nodes at same
     * time
     */
    public boolean isDetachRelationship() {
        return detachRelationship;
    }

    public void setDetachRelationship(boolean detachRelationship) {
        this.detachRelationship = detachRelationship;
    }

    /**
     * Vector Index Name
     */
    public String getVectorIndexName() {
        return vectorIndexName;
    }

    public void setVectorIndexName(String vectorIndexName) {
        this.vectorIndexName = vectorIndexName;
    }

    /**
     * Dimension of Vector Index
     */
    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    /**
     * Similarity Function of Vector Index
     */
    public Neo4jSimilarityFunction getSimilarityFunction() {
        return similarityFunction;
    }

    public void setSimilarityFunction(Neo4jSimilarityFunction similarityFunction) {
        this.similarityFunction = similarityFunction;
    }

    /**
     * Minimum score for Vector Similarity search
     */
    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    /**
     * Maximum results for Vector Similarity search
     */
    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    /**
     * Advanced - Driver
     */
    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    /**
     * Realm - used for Basic Authentication
     */
    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    /**
     * Token - used for Bearer Authentication
     */
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Encoded base64 ticket - used for Kerberos Authentication
     */
    public String getKerberosAuthTicket() {
        return kerberosAuthTicket;
    }

    public void setKerberosAuthTicket(String kerberosAuthTicket) {
        this.kerberosAuthTicket = kerberosAuthTicket;
    }

    /**
     * Cypher Query
     */
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public Neo4jConfiguration copy() {
        try {
            return (Neo4jConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
