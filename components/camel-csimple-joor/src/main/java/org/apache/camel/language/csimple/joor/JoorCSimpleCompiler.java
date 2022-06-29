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
package org.apache.camel.language.csimple.joor;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.StaticService;
import org.apache.camel.language.csimple.CSimpleCodeGenerator;
import org.apache.camel.language.csimple.CSimpleCompiler;
import org.apache.camel.language.csimple.CSimpleExpression;
import org.apache.camel.language.csimple.CSimpleGeneratedCode;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.joor.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jOOR compiler for csimple language.
 */
@JdkService(CSimpleCompiler.FACTORY)
public class JoorCSimpleCompiler extends ServiceSupport implements CSimpleCompiler, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(JoorCSimpleCompiler.class);
    private static final AtomicInteger UUID = new AtomicInteger();
    private Set<String> imports = new TreeSet<>();
    private Map<String, String> aliases;
    private int counter;
    private long taken;

    public Set<String> getImports() {
        return imports;
    }

    public void setImports(Set<String> imports) {
        this.imports = imports;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    @Override
    public void addImport(String imports) {
        this.imports.add(imports);
    }

    @Override
    public void addAliases(String key, String value) {
        this.aliases.put(key, value);
    }

    @Override
    public CSimpleExpression compileExpression(CamelContext camelContext, String script) {
        return doCompile(camelContext, script, false);
    }

    @Override
    public CSimpleExpression compilePredicate(CamelContext camelContext, String script) {
        return doCompile(camelContext, script, true);
    }

    private CSimpleExpression doCompile(CamelContext camelContext, String script, boolean predicate) {
        StopWatch watch = new StopWatch();

        CSimpleExpression answer;
        String className = nextFQN();
        CSimpleGeneratedCode code;
        if (predicate) {
            code = evalCodePredicate(camelContext, className, script);
        } else {
            code = evalCodeExpression(camelContext, className, script);
        }
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Compiling code:\n\n{}\n", code.getCode());
            }
            Reflect ref = Reflect.compile(code.getFqn(), code.getCode());
            Class<?> clazz = ref.type();
            answer = (CSimpleExpression) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            throw new JoorCSimpleCompilationException(code.getFqn(), code.getCode(), e);
        }

        counter++;
        taken += watch.taken();
        return answer;
    }

    private CSimpleGeneratedCode evalCodeExpression(CamelContext camelContext, String fqn, String script) {
        return doEvalCode(camelContext, fqn, script, false);
    }

    private CSimpleGeneratedCode evalCodePredicate(CamelContext camelContext, String fqn, String script) {
        return doEvalCode(camelContext, fqn, script, true);
    }

    private CSimpleGeneratedCode doEvalCode(CamelContext camelContext, String fqn, String script, boolean predicate) {
        // reload script
        script = ScriptHelper.resolveOptionalExternalScript(camelContext, script);
        // trim text
        script = script.trim();

        CSimpleCodeGenerator generator = new CSimpleCodeGenerator();
        if (aliases != null && !aliases.isEmpty()) {
            generator.setAliases(aliases);
        }
        if (imports != null && !imports.isEmpty()) {
            generator.setImports(imports);
        }

        CSimpleGeneratedCode code;
        if (predicate) {
            code = generator.generatePredicate(fqn, script);
        } else {
            code = generator.generateExpression(fqn, script);
        }

        return code;
    }

    private static String nextFQN() {
        return "org.apache.camel.language.csimple.joor.compiled.CSimpleJoorScript" + UUID.incrementAndGet();
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (counter > 0) {
            LOG.info("csimple-joor compiled {} scripts in {}", counter, TimeUtils.printDuration(taken, true));
        }
    }

}
