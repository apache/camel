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
package org.apache.camel.component.sql.stored.template.ast;

import java.lang.reflect.Field;
import java.sql.Types;

import org.apache.camel.component.sql.stored.template.generated.SSPTParserConstants;
import org.apache.camel.component.sql.stored.template.generated.Token;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.StringHelper;
import org.springframework.util.ReflectionUtils;

public final class ParseHelper {

    private ParseHelper() {
    }

    public static int parseSqlType(Token sqlTypeToken, ClassResolver classResolver) {

        String sqlType = sqlTypeToken.toString();

        // if number, then use it(probably Vendor specific SQL-type)
        if (sqlTypeToken.kind == SSPTParserConstants.NUMBER) {
            return Integer.parseInt(sqlType);
        }

        //if contains .
        if (sqlType.contains(".")) {
            String className;
            String fieldName;
            try {
                className = sqlType.substring(0, sqlType.lastIndexOf('.'));
                fieldName = sqlType.substring(sqlType.lastIndexOf('.') + 1);
            } catch (Exception ex) {
                throw new ParseRuntimeException("Failed to parse class.field:" + sqlType);
            }
            try {
                Class<?> clazz = classResolver.resolveMandatoryClass(className);
                return getFieldInt(clazz, fieldName);
            } catch (ClassNotFoundException e) {
                throw new ParseRuntimeException("Class for " + className + " not found", e);
            }
        }

        //Loop-up from "Standard" types
        return getFieldInt(Types.class, sqlType);
    }

    public static Integer parseScale(Token token) {
        try {
            String str = token.toString();
            return Integer.valueOf(str.substring(1, str.length() - 1));
        } catch (Exception ex) {
            throw new ParseRuntimeException("Failed to parse scale from token:" + token.toString(), ex);
        }
    }

    private static int getFieldInt(Class<?> clazz, String sqlType) {
        Field field = ReflectionUtils.findField(clazz, sqlType);
        if (field == null) {
            throw new ParseRuntimeException("Field " + sqlType + " not found from " + clazz.getName());
        }
        try {
            return field.getInt(Types.class);
        } catch (IllegalAccessException e) {
            throw new ParseRuntimeException(e);
        }
    }

    public static String removeQuotes(String token) {
        try {
            return StringHelper.removeLeadingAndEndingQuotes(token);
        } catch (Exception ex) {
            throw new ParseRuntimeException("Failed to remove quotes from token:" + token, ex);
        }
    }
}
