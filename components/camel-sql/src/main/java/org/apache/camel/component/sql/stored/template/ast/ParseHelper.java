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
package org.apache.camel.component.sql.stored.template.ast;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import org.springframework.util.ReflectionUtils;

public final class ParseHelper {

    static final Map<Integer, Class> SQL_TYPE_TO_JAVA_CLASS = new HashMap<>();

    //somekind of mapping here https://docs.oracle.com/cd/E19501-01/819-3659/gcmaz/
    //TODO: test with each SQL_TYPE_TO_JAVA_CLASS that JAVA conversion works!
    static {
        SQL_TYPE_TO_JAVA_CLASS.put(Types.INTEGER, Integer.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.VARCHAR, String.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.BIGINT, Long.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.CHAR, String.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.DECIMAL, BigDecimal.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.BOOLEAN, Boolean.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.DATE, Date.class);
        SQL_TYPE_TO_JAVA_CLASS.put(Types.TIMESTAMP, Date.class);
    }

    private ParseHelper() {
    }

    public static int parseSqlType(String sqlType) {
        Field field = ReflectionUtils.findField(Types.class, sqlType);
        if (field == null) {
            throw new ParseRuntimeException("Field " + sqlType + " not found from java.procedureName.Types");
        }
        try {
            return field.getInt(Types.class);
        } catch (IllegalAccessException e) {
            throw new ParseRuntimeException(e);
        }
    }

    public static Class sqlTypeToJavaType(int sqlType, String sqlTypeStr) {
        Class javaType = SQL_TYPE_TO_JAVA_CLASS.get(sqlType);
        if (javaType == null) {
            throw new ParseRuntimeException("Unable to map SQL type " + sqlTypeStr + " to Java type");
        }
        return javaType;
    }
}
