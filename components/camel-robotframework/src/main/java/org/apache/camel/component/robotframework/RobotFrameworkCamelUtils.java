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
package org.apache.camel.component.robotframework;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

public final class RobotFrameworkCamelUtils {

    private static final String ROBOT_CAMEL_EXCHANGE_NAME = "exchange";
    private static final String ROBOT_VAR_CAMEL_BODY = "body";
    private static final String ROBOT_VAR_CAMEL_HEADERS = "headers";
    private static final String ROBOT_VAR_CAMEL_VARIABLES = "variables";
    private static final String ROBOT_VAR_CAMEL_PROPERTIES = "properties";
    private static final String ROBOT_VAR_FIELD_SEPERATOR = ":";
    private static final String ROBOT_VAR_NESTING_SEPERATOR = ".";

    /**
     * Utility classes should not have a public constructor.
     */
    private RobotFrameworkCamelUtils() {
    }

    @SuppressWarnings("unchecked")
    public static List<String> createRobotVariablesFromCamelExchange(Exchange exchange, boolean allowContextMapAll)
            throws TypeConversionException, NoTypeConversionAvailableException {
        Map<String, Object> variablesMap = ExchangeHelper.createVariableMap(exchange, allowContextMapAll);
        List<String> variableKeyValuePairList = new ArrayList<>();
        for (Map.Entry<String, Object> variableEntry : variablesMap.entrySet()) {
            if (ROBOT_VAR_CAMEL_BODY.equals(variableEntry.getKey())) {
                String bodyVariable = variableEntry.getKey() + ROBOT_VAR_FIELD_SEPERATOR
                                      + exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class,
                                              variableEntry.getValue());
                variableKeyValuePairList.add(bodyVariable);
            } else if (ROBOT_VAR_CAMEL_HEADERS.equals(variableEntry.getKey())) {
                // here the param is the headers map
                createStringValueOfVariablesFromMap(variableKeyValuePairList,
                        ObjectHelper.cast(Map.class, variableEntry.getValue()), exchange, new StringBuilder(),
                        ROBOT_VAR_CAMEL_HEADERS, true);
            } else if (ROBOT_VAR_CAMEL_VARIABLES.equals(variableEntry.getKey())) {
                // here the param is the headers map
                createStringValueOfVariablesFromMap(variableKeyValuePairList, exchange.getVariables(),
                        exchange, new StringBuilder(), ROBOT_VAR_CAMEL_VARIABLES, true);
            } else if (ROBOT_CAMEL_EXCHANGE_NAME.equals(variableEntry.getKey())) {
                // here the param is camel exchange
                createStringValueOfVariablesFromMap(variableKeyValuePairList, exchange.getProperties(),
                        ObjectHelper.cast(Exchange.class, variableEntry.getValue()),
                        new StringBuilder(), ROBOT_VAR_CAMEL_PROPERTIES, true);
            }
        }
        return variableKeyValuePairList;
    }

    @SuppressWarnings("unchecked")
    private static void createStringValueOfVariablesFromMap(
            List<String> list, Map<String, Object> headersMap, Exchange exchange, StringBuilder headerVariableName,
            String baseName,
            boolean includeBaseName)
            throws TypeConversionException, NoTypeConversionAvailableException {
        for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
            if (includeBaseName) {
                headerVariableName.append(baseName);
            }
            headerVariableName.append(ROBOT_VAR_NESTING_SEPERATOR).append(entry.getKey());
            if (entry.getValue() instanceof Map) {
                createStringValueOfVariablesFromMap(list, ObjectHelper.cast(Map.class, entry.getValue()), exchange,
                        headerVariableName, headerVariableName.toString(), false);
            } else {
                headerVariableName.append(ROBOT_VAR_FIELD_SEPERATOR)
                        .append(exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, entry.getValue()));
            }
            list.add(headerVariableName.toString());
            if (includeBaseName) {
                headerVariableName = new StringBuilder();
            } else {
                headerVariableName = new StringBuilder(baseName);
            }
        }
    }

}
