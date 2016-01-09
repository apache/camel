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

import org.apache.camel.component.sql.stored.template.ast.ParseRuntimeException;
import org.apache.camel.component.sql.stored.template.ast.Template;
import org.apache.camel.component.sql.stored.template.generated.ParseException;
import org.apache.camel.component.sql.stored.template.generated.SSPTParser;
import org.apache.camel.util.LRUCache;
import org.springframework.jdbc.core.JdbcTemplate;

public class TemplateStoredProcedureFactory {

    public static final int TEMPLATE_CACHE_DEFAULT_SIZE = 200;
    private final JdbcTemplate jdbcTemplate;
    private LRUCache<String, TemplateStoredProcedure> templateCache = new LRUCache<String, TemplateStoredProcedure>(TEMPLATE_CACHE_DEFAULT_SIZE);

    public TemplateStoredProcedureFactory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TemplateStoredProcedure createFromString(String string) {
        TemplateStoredProcedure fromCache = templateCache.get(string);

        if (fromCache != null) {
            return fromCache;
        }

        Template sptpRootNode = parseTemplate(string);
        TemplateStoredProcedure ret = new TemplateStoredProcedure(jdbcTemplate, sptpRootNode);

        templateCache.put(string, ret);

        return ret;
    }

    public Template parseTemplate(String template) {
        try {
            SSPTParser parser = new SSPTParser(new StringReader(template));
            return validate(parser.parse());
        } catch (ParseException parseException) {
            throw new ParseRuntimeException(parseException);
        }
    }

    private Template validate(Template input) {
        return input;
    }

}
