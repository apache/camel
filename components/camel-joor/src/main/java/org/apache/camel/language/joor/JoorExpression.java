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

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ScriptHelper;
import org.joor.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoorExpression extends ExpressionAdapter {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Logger LOG = LoggerFactory.getLogger(JoorExpression.class);
    private static Boolean JAVA8;

    private final String text;
    private String className;
    private String code;
    private Reflect compiled;
    private Method method;

    private Class<?> resultType;
    private boolean preCompile = true;
    private boolean singleQuotes = true;

    public JoorExpression(String text) {
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
    public Object evaluate(Exchange exchange) {
        try {
            Reflect ref = compiled;
            if (ref == null) {
                this.className = nextFQN();
                this.code = evalCode(exchange.getContext(), className, text);
                LOG.trace(code);
                ref = compile(className, code);
                method = ref.type().getMethod("evaluate", CamelContext.class, Exchange.class, Message.class, Object.class);
            }
            // optimize as we call the same method all the time so we dont want to find the method every time as joor would do
            // if you use its call method
            Object out = method.invoke(null, exchange.getContext(), exchange, exchange.getIn(), exchange.getIn().getBody());
            if (out != null && resultType != null) {
                return exchange.getContext().getTypeConverter().convertTo(resultType, exchange, out);
            } else {
                return out;
            }
        } catch (Exception e) {
            throw new JoorExpressionEvaluationException(this, className, code, exchange, e);
        }
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
            this.className = nextFQN();
            this.code = evalCode(context, className, text);
            LOG.debug(code);
            try {
                this.compiled = compile(className, code);
                this.method = compiled.type().getMethod("evaluate", CamelContext.class, Exchange.class, Message.class,
                        Object.class);
            } catch (NoSuchMethodException e) {
                throw new JoorCompilationException(className, code, e);
            }
        }
    }

    private Reflect compile(String fqn, String code) {
        try {
            return Reflect.compile(fqn, code);
        } catch (Exception e) {
            throw new JoorCompilationException(fqn, code, e);
        }
    }

    private String evalCode(CamelContext camelContext, String fqn, String text) {
        String qn = fqn.substring(0, fqn.lastIndexOf('.'));
        String name = fqn.substring(fqn.lastIndexOf('.') + 1);

        // reload script
        text = ScriptHelper.resolveOptionalExternalScript(camelContext, text);

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

    private static int getJavaMajorVersion() {
        String javaSpecVersion = System.getProperty("java.specification.version");
        return javaSpecVersion.contains(".")
                ? Integer.parseInt(javaSpecVersion.split("\\.")[1]) : Integer.parseInt(javaSpecVersion);
    }

    private static String nextFQN() {
        return "org.apache.camel.language.joor.compiled.JoorLanguage" + COUNTER.incrementAndGet();
    }

}
