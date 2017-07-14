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
package org.apache.camel.component.sql.stored.template;

import java.io.StringReader;

import org.apache.camel.CamelContext;
import org.apache.camel.component.sql.stored.template.ast.ParseRuntimeException;
import org.apache.camel.component.sql.stored.template.ast.Template;
import org.apache.camel.component.sql.stored.template.generated.ParseException;
import org.apache.camel.component.sql.stored.template.generated.SSPTParser;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.ObjectHelper;

public class TemplateParser {

    private final ClassResolver classResolver;

    public TemplateParser(ClassResolver classResolver) {
        ObjectHelper.notNull(classResolver, "classResolver");
        this.classResolver = classResolver;
    }

    public Template parseTemplate(String template) {
        try {
            SSPTParser parser = new SSPTParser(new StringReader(template), classResolver);
            Template ret = validate(parser.parse());

            return ret;

        } catch (ParseException parseException) {
            throw new ParseRuntimeException(parseException);
        }
    }

    private Template validate(Template input) {
        return input;
    }


}
