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
package org.apache.camel;

/**
 * An exception caused when a mandatory variable is not available
 */
public class NoSuchVariableException extends CamelExchangeException {

    private final String variableName;
    private final transient Class<?> type;

    public NoSuchVariableException(Exchange exchange, String variableName) {
        super(String.format(
                "No '%s' variable available", variableName),
              exchange);
        this.variableName = variableName;
        this.type = null;
    }

    public NoSuchVariableException(Exchange exchange, String variableName, Class<?> type) {
        super(String.format(
                "No '%s' variable available of type: %s",
                variableName,
                type.getName()),
              exchange);
        this.variableName = variableName;
        this.type = type;
    }

    public String getVariableName() {
        return variableName;
    }

    public Class<?> getType() {
        return type;
    }

}
