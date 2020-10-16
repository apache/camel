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

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.joor.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoorExpression extends ExpressionAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(JoorExpression.class);
    private static Boolean JAVA8;

    private final String fqn;
    private final String text;
    private String code;
    private Reflect compiled;

    private Class<?> resultType;
    private boolean preCompile = true;
    private boolean singleQuotes = true;

    public JoorExpression(String fqn, String text) {
        this.fqn = fqn;
        this.text = text;
    }

    @Override
    public String toString() {
        return "joor:" + text;
    }

    public boolean isPreCompile() {
        return preCompile;
    }

    public void setPreCompile(boolean preCompile) {
        this.preCompile = preCompile;
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

        if (preCompile) {
            this.code = evalCode(context, text);
            LOG.debug(code);
            this.compiled = compile(fqn, code);
        }
    }

    private Reflect compile(String fqn, String code) {
        try {
            return Reflect.compile(fqn, code);
        } catch (Exception e) {
            throw new JoorCompilationException(fqn, code, e);
        }
    }

    private String evalCode(CamelContext camelContext, String text) {
        String qn = fqn.substring(0, fqn.lastIndexOf('.'));
        String name = fqn.substring(fqn.lastIndexOf('.') + 1);

        if (text.startsWith("resource:")) {
            String url = text.substring(9);
            try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, url)) {
                text = IOHelper.loadText(is);
            } catch (IOException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        // trim text
        text = text.trim();

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
        if (!text.endsWith("}") && !text.endsWith(";")) {
            sb.append(";");
        }
        sb.append("\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");

        return sb.toString();
    }

    @Override
    public Object evaluate(Exchange exchange) {
        try {
            Reflect ref = compiled;
            if (ref == null) {
                String eval = evalCode(exchange.getContext(), text);
                ref = compile(fqn, eval);
            }
            Object out = ref
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
