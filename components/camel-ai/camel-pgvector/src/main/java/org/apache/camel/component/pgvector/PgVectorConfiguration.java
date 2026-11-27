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
package org.apache.camel.component.pgvector;

import javax.sql.DataSource;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class PgVectorConfiguration implements Cloneable {

    @Metadata(label = "producer",
              description = "The action to perform. The CamelPgVectorAction header, when set, wins over this option.")
    @UriParam
    private PgVectorAction action;

    @Metadata(autowired = true,
              description = "The javax.sql.DataSource of the PostgreSQL database. The endpoint fails to start when no"
                            + " DataSource is set or autowired. The producer takes a new connection from it for each message,"
                            + " so a pooling DataSource is recommended.")
    @UriParam
    private DataSource dataSource;

    @Metadata(label = "producer",
              description = "The number of dimensions of the embedding column that CREATE_TABLE creates, as vector(n). It"
                            + " must match the size of the vectors of the embedding model, as PostgreSQL rejects a vector of"
                            + " another size. The other actions do not use it.")
    @UriParam(defaultValue = "384")
    private int dimension = 384;

    @Metadata(label = "producer",
              description = "The distance function of SIMILARITY_SEARCH and of the HNSW index that CREATE_INDEX creates:"
                            + " COSINE (cosine distance), EUCLIDEAN (L2 distance) or INNER_PRODUCT (the negative inner product,"
                            + " so the closest records have the lowest distance). A search uses the index only when both use"
                            + " the same distance type.")
    @UriParam(defaultValue = "COSINE")
    private PgVectorDistanceType distanceType = PgVectorDistanceType.COSINE;

    public PgVectorAction getAction() {
        return action;
    }

    public void setAction(PgVectorAction action) {
        this.action = action;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public PgVectorDistanceType getDistanceType() {
        return distanceType;
    }

    public void setDistanceType(PgVectorDistanceType distanceType) {
        this.distanceType = distanceType;
    }

    // ************************
    //
    // Clone
    //
    // ************************
    public PgVectorConfiguration copy() {
        try {
            return (PgVectorConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
