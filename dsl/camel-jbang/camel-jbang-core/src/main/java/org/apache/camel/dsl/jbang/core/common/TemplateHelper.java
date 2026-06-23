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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Pattern;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

/**
 * Utility for processing FreeMarker templates used in code generation.
 * <p>
 * Uses square bracket syntax for both interpolation ([=name]) and directives ([#if]...[/#if]) to avoid conflicts with
 * ${...} (Maven/Camel expressions) and &lt;...&gt; (XML tags) in generated content.
 */
public final class TemplateHelper {

    // Matches <#-- ... --> comment blocks that appear as literal text in square bracket tag syntax mode
    private static final Pattern ANGLE_BRACKET_COMMENT = Pattern.compile("\\A<#--.*?-->\\s*", Pattern.DOTALL);

    private static final Configuration CFG;

    static {
        CFG = new Configuration(Configuration.VERSION_2_3_34);
        CFG.setTemplateLoader(new ClassTemplateLoader(TemplateHelper.class, "/templates"));
        CFG.setDefaultEncoding("UTF-8");
        CFG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        CFG.setLogTemplateExceptions(false);
        // Use square bracket syntax to avoid conflicts with ${...} and <...> in templates
        CFG.setInterpolationSyntax(Configuration.SQUARE_BRACKET_INTERPOLATION_SYNTAX);
        CFG.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
    }

    private TemplateHelper() {
    }

    /**
     * Process a FreeMarker template from classpath resources/templates/ directory.
     *
     * @param  templateName the template file name (e.g. "main-pom.ftl")
     * @param  model        the data model
     * @return              the processed template output
     */
    public static String processTemplate(String templateName, Map<String, Object> model) throws IOException {
        try {
            Template template = CFG.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            // Strip any <#-- ... --> license header that appears as literal text
            // (angle bracket comments are not recognized in square bracket tag syntax mode)
            return ANGLE_BRACKET_COMMENT.matcher(writer.toString()).replaceFirst("");
        } catch (TemplateException e) {
            throw new IOException("Failed to process template: " + templateName, e);
        }
    }
}
