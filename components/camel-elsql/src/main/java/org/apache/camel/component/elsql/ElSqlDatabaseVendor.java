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
package org.apache.camel.component.elsql;

import com.opengamma.elsql.ElSqlConfig;

public enum ElSqlDatabaseVendor {

    Default, Postgres, HSql, MySql, Oracle, SqlServer2008, Veritca;

    ElSqlConfig asElSqlConfig() {
        if (Postgres.equals(this)) {
            return ElSqlConfig.POSTGRES;
        } else if (HSql.equals(this)) {
            return ElSqlConfig.HSQL;
        } else if (MySql.equals(this)) {
            return ElSqlConfig.MYSQL;
        } else if (Oracle.equals(this)) {
            return ElSqlConfig.ORACLE;
        } else if (SqlServer2008.equals(this)) {
            return ElSqlConfig.SQL_SERVER_2008;
        } else if (Veritca.equals(this)) {
            return ElSqlConfig.VERTICA;
        } else {
            return ElSqlConfig.DEFAULT;
        }
    }
}
