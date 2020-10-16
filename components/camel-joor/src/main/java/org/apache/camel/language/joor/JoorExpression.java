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
package org.apache.camel.language.joor;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.support.ExpressionAdapter;
import org.joor.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoorExpression extends ExpressionAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(JoorExpression.class);
    private static Boolean JAVA8;

    private final String fqn;
    private final String text;
    private Reflect compiled;

    private Class<?> resultType;
    private boolean singleQuotes = true;

    public JoorExpression(String fqn, String text) {
        this.fqn = fqn;
        this.text = text;
    }

    @Override
    public String toString() {
        return "joor:" + text;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public boolean isSingleQuotes() {
        return singleQuotes;
    }

    public void setSingleQuotes(boolean singleQuotes) {
        this.singleQuotes = singleQuotes;
    }

    @Override
    public void init(CamelContext context) {
        super.init(context);

        if (JAVA8 == null) {
            JAVA8 = getJavaMajorVersion() == 8;
            if (JAVA8) {
                throw new UnsupportedOperationException("Java 8 is not supported. Use Java 11 or higher");
            }
        }

        String qn = fqn.substring(0, fqn.lastIndexOf('.'));
        String name = fqn.substring(fqn.lastIndexOf('.') + 1);

        //  wrap text into a class method we can call
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("package ").append(qn).append(";\n");
        sb.append("\n");
        sb.append("import org.apache.camel.*;\n");
        sb.append("\n");
        sb.append("public class ").append(name).append(" {\n");
        sb.append("\n");
        sb.append("\n");
        sb.append(
                "    public static Object evaluate(CamelContext context, Exchange exchange, Message message, Object body) throws Exception {\n");
        sb.append("        ");
        if (!text.contains("return ")) {
            sb.append("return ");
        }
        if (singleQuotes) {
            // single quotes instead of double quotes, as its very annoying for string in strings
            String quoted = text.replace('\'', '"');
            sb.append(quoted);
        } else {
            sb.append(text);
        }
        if (!text.endsWith(";")) {
            sb.append(";");
        }
        sb.append("\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");

        String code = sb.toString();
        LOG.debug(code);

        compiled = Reflect.compile(fqn, code);
    }

    @Override
    public Object evaluate(Exchange exchange) {
        try {
            Object out = compiled
                    .call("evaluate", exchange.getContext(), exchange, exchange.getIn(), exchange.getIn().getBody()).get();
            if (out != null && resultType != null) {
                return exchange.getContext().getTypeConverter().convertTo(resultType, exchange, out);
            } else {
                return out;
            }
        } catch (Exception e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
    }

    private static int getJavaMajorVersion() {
        String javaSpecVersion = System.getProperty("java.specification.version");
        return javaSpecVersion.contains(".")
                ? Integer.parseInt(javaSpecVersion.split("\\.")[1]) : Integer.parseInt(javaSpecVersion);
    }

}
