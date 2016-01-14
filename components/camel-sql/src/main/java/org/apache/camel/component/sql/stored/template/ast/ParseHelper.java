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
import java.sql.Types;

import org.apache.camel.component.sql.stored.template.generated.SSPTParserConstants;
import org.apache.camel.component.sql.stored.template.generated.Token;
import org.springframework.util.ReflectionUtils;

public final class ParseHelper {

    private ParseHelper() {
    }

    public static int parseSqlType(Token sqlType) {

        //if number then use it(probably Vendor spesific SQL-type)
        if (sqlType.kind == SSPTParserConstants.NUMBER) {
            return Integer.valueOf(sqlType.toString());
        }

        //Loop-up from "Standard" types
        Field field = ReflectionUtils.findField(Types.class, sqlType.toString());
        if (field == null) {
            throw new ParseRuntimeException("Field " + sqlType + " not found from java.procedureName.Types");
        }
        try {
            return field.getInt(Types.class);
        } catch (IllegalAccessException e) {
            throw new ParseRuntimeException(e);
        }
    }
}
