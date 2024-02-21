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
package org.apache.camel.component.mybatis;

import org.apache.camel.spi.Metadata;

/**
 * MyBatis constants.
 */
public final class MyBatisConstants {

    // The schemes
    public static final String SCHEME_MYBATIS_BEAN = "mybatis-bean";
    public static final String SCHEME_MYBATIS = "mybatis";

    @Metadata(label = "producer", description = "The *response* returned from MtBatis in any of the operations. For\n" +
                                                "instance an `INSERT` could return the auto-generated key, or number of\n" +
                                                "rows etc.",
              javaType = "Object")
    public static final String MYBATIS_RESULT = "CamelMyBatisResult";
    @Metadata(description = "The *statementName* used (for example: insertAccount).", javaType = "String",
              applicableFor = SCHEME_MYBATIS)
    public static final String MYBATIS_STATEMENT_NAME = "CamelMyBatisStatementName";

    private MyBatisConstants() {
        // Utility class
    }

}
