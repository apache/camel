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
package org.apache.camel.component.elsql;

import org.apache.camel.component.sql.SqlConstants;
import org.apache.camel.spi.Metadata;

public final class ElsqlConstants {

    @Metadata(label = "producer", description = "The number of rows updated for `update` operations, returned as an\n" +
                                                "`Integer` object.",
              javaType = "Integer")
    public static final String SQL_UPDATE_COUNT = SqlConstants.SQL_UPDATE_COUNT;
    @Metadata(label = "producer", description = "The number of rows returned for `select` operations, returned as an\n" +
                                                "`Integer` object.",
              javaType = "Integer")
    public static final String SQL_ROW_COUNT = SqlConstants.SQL_ROW_COUNT;

    private ElsqlConstants() {
        // Utility class
    }
}
