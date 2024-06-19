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
package org.apache.camel.updates.camel41;

import java.util.regex.Pattern;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.camel.updates.AbstractCamelJavaVisitor;
import org.apache.camel.updates.RecipesUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/**
 * Recipe migrating changes between Camel 4.3 to 4.4, for more details see the
 * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core" >documentation</a>.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class CamelCoreRecipe extends Recipe {

    private static final String M_TO = "org.apache.camel.model.ProcessorDefinition to(..)";
    private static final String M_FROM = "org.apache.camel.model.ProcessorDefinition from(..)";
    private static final String AWS2_URL_WITH_QUEUE_REGEXP = "(aws2-sns://[a-zA-z]+?.*)queueUrl=https://(.+)";
    private static final Pattern AWS2_URL_WITH_QUEUE_URL = Pattern.compile(AWS2_URL_WITH_QUEUE_REGEXP);

    @Override
    public String getDisplayName() {
        return "Camel Core changes";
    }

    @Override
    public String getDescription() {
        return "Apache Camel Core migration from version 4.0 to 4.1.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return RecipesUtil.newVisitor(new AbstractCamelJavaVisitor() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext context) {
                J.Literal l = super.visitLiteral(literal, context);

                //is it possible to precondition that aws2 is present?
                if (JavaType.Primitive.String.equals(l.getType())
                        && AWS2_URL_WITH_QUEUE_URL.matcher((String) l.getValue()).matches()) {
                    String newUrl
                            = ((String) l.getValue()).replaceFirst(AWS2_URL_WITH_QUEUE_REGEXP, "$1queueArn=arn:aws:sqs:$2");
                    l = RecipesUtil.createStringLiteral(newUrl);
                }

                return l;
            }
        });
    }
}
