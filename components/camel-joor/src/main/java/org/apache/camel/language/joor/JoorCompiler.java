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
import org.apache.camel.StaticService;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;
import org.joor.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoorCompiler extends ServiceSupport implements StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(JoorCompiler.class);
    private static final AtomicInteger UUID = new AtomicInteger();
    private int counter;
    private long taken;

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (counter > 0) {
            LOG.info("jOOR language compiled {} scripts in {} millis", counter, taken);
        }
    }

    public Method compile(CamelContext camelContext, String script, boolean singleQuotes) {
        StopWatch watch = new StopWatch();

        Method answer;
        String className = nextFQN();
        String code = evalCode(camelContext, className, script, singleQuotes);
        try {
            LOG.trace(code);
            Reflect ref = Reflect.compile(className, code);
            answer = ref.type().getMethod("evaluate", CamelContext.class, Exchange.class, Message.class, Object.class);
        } catch (Exception e) {
            throw new JoorCompilationException(className, code, e);
        }

        counter++;
        taken += watch.taken();
        return answer;
    }

    private String evalCode(CamelContext camelContext, String fqn, String script, boolean singleQuotes) {
        String qn = fqn.substring(0, fqn.lastIndexOf('.'));
        String name = fqn.substring(fqn.lastIndexOf('.') + 1);

        // reload script
        script = ScriptHelper.resolveOptionalExternalScript(camelContext, script);

        // trim text
        script = script.trim();

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
        if (!script.contains("return ")) {
            sb.append("return ");
        }
        if (singleQuotes) {
            // single quotes instead of double quotes, as its very annoying for string in strings
            String quoted = script.replace('\'', '"');
            sb.append(quoted);
        } else {
            sb.append(script);
        }
        if (!script.endsWith("}") && !script.endsWith(";")) {
            sb.append(";");
        }
        sb.append("\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");

        return sb.toString();
    }

    private static String nextFQN() {
        return "org.apache.camel.language.joor.compiled.JoorLanguage" + UUID.incrementAndGet();
    }

}
