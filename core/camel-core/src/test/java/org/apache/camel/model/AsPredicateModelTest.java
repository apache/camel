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
package org.apache.camel.model;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.model.validator.PredicateValidatorDefinition;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Language;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.support.scan.DefaultPackageScanClassResolver;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The expression options the model marks with {@link AsPredicate} (asPredicate in the model JSON the catalog ships) are
 * the ones the reifiers evaluate with createPredicate, so the annotation and the runtime cannot drift apart.
 */
public class AsPredicateModelTest {

    /**
     * The one EIP the annotation cannot describe: the loop expression is a predicate only when doWhile is enabled.
     */
    private static final String LOOP = "loop.expression";

    /** Records for each site (the expression text) whether the reifier asked for a predicate or an expression. */
    private static class SpyLanguage implements Language {
        private final Set<String> predicates = new TreeSet<>();
        private final Set<String> expressions = new TreeSet<>();

        @Override
        public Predicate createPredicate(String expression) {
            predicates.add(expression);
            return PredicateBuilder.constant(true);
        }

        @Override
        public Expression createExpression(String expression) {
            expressions.add(expression);
            return ExpressionBuilder.constantExpression("1");
        }
    }

    /** A spy expression at the given site (model.option) that binds to the predicate overloads of the DSL. */
    private static Predicate site(String site) {
        return new LanguageExpression("spy", site);
    }

    /** A spy expression at the given site for the options set as expression sub element (handled, retryWhile). */
    private static ExpressionSubElementDefinition subElement(String site) {
        return new ExpressionSubElementDefinition(new LanguageExpression("spy", site));
    }

    /** A spy expression at the given site that binds to the expression overloads of the DSL. */
    private static Expression expressionSite(String site) {
        return new LanguageExpression("spy", site);
    }

    @Test
    public void testAsPredicateMatchesReifier() throws Exception {
        Map<String, String> marked = findAsPredicateOptions();
        // the expression options marked as predicate; the element options (choice.when, doCatch.onWhen) hold a when
        // or onWhen node whose own expression is marked
        Set<String> expected = new TreeSet<>();
        Set<String> holders = new TreeSet<>();
        marked.forEach((site, kind) -> ("expression".equals(kind) ? expected : holders).add(site));
        assertTrue(expected.size() >= 9, "Expected at least 9 predicate expression options, was: " + expected);

        SpyLanguage spy = new SpyLanguage();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("spy", spy);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // the fluent handled(Predicate), retryWhile(Predicate) and completionPredicate(Predicate) keep
                    // the predicate as is (no language to spy on) so those are set as expression sub elements
                    OnExceptionDefinition handled = onException(Exception.class).onWhen(site("onException.onWhen"));
                    handled.setHandled(subElement("onException.handled"));
                    handled.setRetryWhile(subElement("onException.retryWhile"));
                    onException(IllegalStateException.class).setContinued(subElement("onException.continued"));
                    onCompletion().onWhen(site("onWhen.expression")).to("mock:done");
                    intercept().when(site("intercept.onWhen")).log("intercept");
                    interceptFrom().when(site("interceptFrom.onWhen")).log("interceptFrom");
                    interceptSendToEndpoint("mock:*").when(site("interceptSendToEndpoint.onWhen"))
                            .log("interceptSendToEndpoint");

                    AggregateDefinition aggregate
                            = from("direct:start")
                            .filter(site("filter.expression")).log("filter").end()
                            .choice().when(site("when.expression")).log("when").end()
                            .validate(site("validate.expression"))
                            .loopDoWhile(site(LOOP)).log("loop").end()
                            .doTry().log("try").doCatch(Exception.class).onWhen(site("doCatch.onWhen")).log("catch")
                            .endDoTry().end()
                            .aggregate(constant("key"), AggregationStrategies.useLatest());
                    aggregate.setCompletionPredicate(subElement("aggregate.completionPredicate"));
                    aggregate.log("aggregate").end()
                            // expression sites, for contrast
                            .split(expressionSite("split.expression")).log("split").end()
                            .transform(expressionSite("transform.expression"))
                            .setBody(expressionSite("setBody.expression"))
                            .to("mock:result");
                }
            });
            PredicateValidatorDefinition validator = new PredicateValidatorDefinition();
            validator.setType("spy");
            validator.setExpression(new LanguageExpression("spy", "predicateValidator.expression"));
            context.registerValidator(validator);
            context.start();
        }

        // every marked option was evaluated as a predicate, and only as a predicate
        for (String site : expected) {
            assertTrue(spy.predicates.contains(site), site + " is marked asPredicate but the reifier did not create a"
                                                      + " predicate for it (is the test missing the site?)");
            assertFalse(spy.expressions.contains(site), site + " is marked asPredicate but is evaluated as expression");
        }
        // every site evaluated as a predicate is marked, or holds a node whose expression is
        for (String site : spy.predicates) {
            if (LOOP.equals(site)) {
                continue;
            }
            assertTrue(expected.contains(site) || holders.contains(site),
                    site + " is evaluated as predicate but not marked with @AsPredicate");
        }
        // the contrast sites are expressions and not marked
        Set<String> contrast = Set.of("split.expression", "transform.expression", "setBody.expression");
        assertTrue(spy.expressions.containsAll(contrast), "Expression sites not evaluated: " + spy.expressions);
        assertTrue(Collections.disjoint(spy.predicates, contrast), "Expression sites evaluated as predicate");
        assertFalse(marked.containsKey(LOOP), "loop is a predicate only with doWhile and cannot be marked");
        assertEquals("expression", marked.get("predicateValidator.expression"));
    }

    /** The options with asPredicate in the model JSON, as model.option to the kind of the option. */
    private static Map<String, String> findAsPredicateOptions() throws Exception {
        Map<String, String> answer = new TreeMap<>();
        DefaultPackageScanClassResolver resolver = new DefaultPackageScanClassResolver();
        resolver.start();
        for (Class<?> clazz : resolver.findAnnotated(XmlRootElement.class, Constants.JAXB_CONTEXT_PACKAGES.split(":"))) {
            String name = clazz.getAnnotation(XmlRootElement.class).name();
            String path = "META-INF/" + clazz.getPackageName().replace('.', '/') + "/" + name + ".json";
            InputStream is = AsPredicateModelTest.class.getClassLoader().getResourceAsStream(path);
            if (is == null) {
                continue;
            }
            JsonObject json = (JsonObject) Jsoner.deserialize(IOHelper.loadText(is));
            JsonObject properties = json.getMap("properties");
            for (String option : properties.keySet()) {
                JsonObject p = properties.getMap(option);
                if (p.getBooleanOrDefault("asPredicate", false)) {
                    answer.put(name + "." + option, p.getString("kind"));
                }
            }
        }
        return answer;
    }

}
