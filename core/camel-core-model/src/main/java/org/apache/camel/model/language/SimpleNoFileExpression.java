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

package org.apache.camel.model.language;

import jakarta.xml.bind.annotation.XmlTransient;

/**
 * The simple language but without support for using the file based functions. This is used in some special situations
 * with EIPs such as poll/pollEnrich. This language is not exposed as a public standard language and are only intended
 * for internal use.
 */
@XmlTransient
public final class SimpleNoFileExpression extends TypedExpressionDefinition {

    public SimpleNoFileExpression(SimpleExpression expression) {
        super(expression);
    }

    @Override
    public String getLanguage() {
        return "simple-no-file";
    }
}
