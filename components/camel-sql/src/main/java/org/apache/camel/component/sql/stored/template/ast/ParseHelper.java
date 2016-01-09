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
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Types;

import org.springframework.util.ReflectionUtils;

public final class ParseHelper {

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
        //TODO: as rest of types.
        //TODO: add test for each type.
        Class ret;
        switch (sqlType) {
        case Types.INTEGER:
            ret = Integer.class;
            break;
        case Types.VARCHAR:
            ret = String.class;
            break;
        case Types.BIGINT:
            ret = BigInteger.class;
            break;
        case Types.CHAR:
            ret = String.class;
            break;
        case Types.BOOLEAN:
            ret = Boolean.class;
            break;
        case Types.DATE:
            ret = Date.class;
            break;
        case Types.TIMESTAMP:
            ret = Date.class;
            break;
        default:
            throw new ParseRuntimeException("Unable to map SQL type " + sqlTypeStr + " to Java type");
        }

        return ret;
    }

}
