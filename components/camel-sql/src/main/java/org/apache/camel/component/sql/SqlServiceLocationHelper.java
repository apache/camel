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
package org.apache.camel.component.sql;

import javax.sql.DataSource;

import org.apache.camel.spi.BeanIntrospection;
import org.springframework.jdbc.datasource.AbstractDriverBasedDataSource;

public class SqlServiceLocationHelper {

    public static String getJDBCURLFromDataSource(BeanIntrospection bi, DataSource ds) {
        if (ds == null) {
            return null;
        }
        if (ds instanceof AbstractDriverBasedDataSource ads) {
            return ads.getUrl();
        }

        Object url = bi.getOrElseProperty(ds, "url", null, false);
        if (url == null) {
            url = bi.getOrElseProperty(ds, "jdbcUrl", null, false);
        }
        if (url != null) {
            return url.toString();
        } else {
            // nested which can be wrapped in connection pooling
            DataSource ncf = (DataSource) bi.getOrElseProperty(ds, "dataSource", null, false);
            if (ncf != null) {
                return getJDBCURLFromDataSource(bi, ncf);
            }
        }
        return null;
    }

    public static String getUsernameFromConnectionFactory(BeanIntrospection bi, DataSource ds) {
        if (ds == null) {
            return null;
        }

        if (ds instanceof AbstractDriverBasedDataSource ads) {
            return ads.getUsername();
        }

        Object user = bi.getOrElseProperty(ds, "user", null, false);
        if (user == null) {
            user = bi.getOrElseProperty(ds, "username", null, false);
        }
        if (user == null) {
            user = bi.getOrElseProperty(ds, "userName", null, false);
        }
        if (user != null) {
            return user.toString();
        } else {
            // nested which can be wrapped in connection pooling
            DataSource ncf = (DataSource) bi.getOrElseProperty(ds, "dataSource", null, false);
            if (ncf != null) {
                return getUsernameFromConnectionFactory(bi, ncf);
            }
        }
        return null;
    }

}
