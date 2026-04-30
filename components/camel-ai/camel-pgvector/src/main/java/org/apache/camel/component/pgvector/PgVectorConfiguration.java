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

    @Metadata(autowired = true,
              description = "The DataSource to use for connecting to the PostgreSQL database with pgvector extension.")
    @UriParam
    private DataSource dataSource;

    @Metadata(label = "producer",
              description = "The dimension of the vectors to store.")
    @UriParam(defaultValue = "384")
    private int dimension = 384;

    @Metadata(label = "producer",
              description = "The distance type to use for similarity search.")
    @UriParam(defaultValue = "COSINE")
    private PgVectorDistanceType distanceType = PgVectorDistanceType.COSINE;

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * The DataSource to use for connecting to the PostgreSQL database with pgvector extension.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int getDimension() {
        return dimension;
    }

    /**
     * The dimension of the vectors to store.
     */
    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public PgVectorDistanceType getDistanceType() {
        return distanceType;
    }

    /**
     * The distance type to use for similarity search. Supported values: COSINE, EUCLIDEAN, INNER_PRODUCT.
     */
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
