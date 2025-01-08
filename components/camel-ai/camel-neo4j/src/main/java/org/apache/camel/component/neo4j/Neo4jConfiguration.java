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
    @UriParam
    private String dbUri;

    @UriParam(description = "Basic authentication database user", displayName = "Database user", secret = true)
    private String dbUser;

    @UriParam(description = "Basic authentication database password", displayName = "Database password", secret = true)
    private String dbPassword;
    @UriParam(description = "Basic authentication database realm", displayName = "Database user", secret = true)
    private String realm;

    @UriParam(description = "Bearer authentication database realm", displayName = "Realm", secret = true)
    private String token;

    @UriParam(description = "Kerberos Authentication encoded base64 ticket", displayName = "Encoded base64 ticket",
              secret = true)
    private String base64;

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
     *
     * @return
     */
    public String getDbUri() {
        return dbUri;
    }

    public void setDbUri(String dbUri) {
        this.dbUri = dbUri;
    }

    /**
     * User of the database - used for Basic Authentication
     *
     * @return
     */
    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    /**
     * Password for dbUser - used for Basic Authentication
     *
     * @return
     */
    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    /**
     * Node Label
     *
     * @return
     */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Node alias
     *
     * @return
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
     *
     * @return
     */
    public boolean isDetachRelationship() {
        return detachRelationship;
    }

    public void setDetachRelationship(boolean detachRelationship) {
        this.detachRelationship = detachRelationship;
    }

    /**
     * Vector Index Name
     *
     * @return
     */
    public String getVectorIndexName() {
        return vectorIndexName;
    }

    public void setVectorIndexName(String vectorIndexName) {
        this.vectorIndexName = vectorIndexName;
    }

    /**
     * Dimension of Vector Index
     *
     * @return
     */
    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    /**
     * Similarity Function of Vector Index
     *
     * @return
     */
    public Neo4jSimilarityFunction getSimilarityFunction() {
        return similarityFunction;
    }

    public void setSimilarityFunction(Neo4jSimilarityFunction similarityFunction) {
        this.similarityFunction = similarityFunction;
    }

    /**
     * Minimum score for Vector Similarity search
     *
     * @return
     */
    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    /**
     * Maximum results for Vector Similarity search
     *
     * @return
     */
    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    /**
     * Advanced - Driver
     *
     * @return
     */
    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    /**
     * realm - used for Basic Authentication
     *
     * @return
     */
    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    /**
     * token - used for Bearer Authentication
     *
     * @return
     */
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Encoded base64 ticket - used for Kerberos Authentication
     *
     * @return
     */
    public String getBase64() {
        return base64;
    }

    public void setBase64(String base64) {
        this.base64 = base64;
    }

    /**
     * Cypher Query
     *
     * @return
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
