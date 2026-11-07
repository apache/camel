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
package org.apache.camel.component.chaos;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.component.chaos.ChaosModel.Placeholder;
import org.apache.camel.component.chaos.ChaosModel.Recipe;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.component.chaos.ChaosRule.Target;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The model of the chaos rules ({@link ChaosModel}): the build wrote it, it agrees with the parser, and every recipe
 * parses.
 */
class ChaosModelTest {

    private static final Path GENERATED = Path.of("src/generated/resources").resolve(ChaosModel.FILE);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z]+)}");
    private static final long MAX = TimeUnit.MINUTES.toMillis(10);

    @Test
    void theBuildWroteTheModel() throws Exception {
        // the generate-chaos-model goal of camel-package-maven-plugin writes it into the jar and src/generated
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(ChaosModel.FILE)) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(ChaosModel.toJson());
        }
        assertThat(Files.readString(GENERATED, StandardCharsets.UTF_8)).isEqualTo(ChaosModel.toJson());
    }

    @Test
    void theModelIsValidJsonWithTheChaosKey() throws Exception {
        JsonObject root = (JsonObject) Jsoner.deserialize(ChaosModel.toJson());
        assertThat(root).containsOnlyKeys("chaos");
        assertThat(chaos()).containsKeys("targets", "hooks", "faults", "options", "expectation", "limits", "recipes");
    }

    @Test
    void theHooksAndFaultsAreThoseOfTheParser() {
        JsonArray hooks = (JsonArray) chaos().get("hooks");
        assertThat(hooks).hasSize(Hook.values().length);
        for (Object o : hooks) {
            JsonObject hook = (JsonObject) o;
            Hook h = hook(hook.getString("name"));
            assertThat(hook.getBoolean("onlyWithAt")).isEqualTo(h == Hook.DLQ || h == Hook.REPLY);
            for (Object t : (JsonArray) hook.get("targets")) {
                Target target = target((String) t);
                Set<String> faults = new HashSet<>();
                ChaosRuleParser.offered(target).get(h).forEach(f -> faults.add(f.key));
                assertThat(new HashSet<>((JsonArray) hook.get("faults"))).containsAll(faults);
            }
        }
        assertThat(names("faults")).containsExactly("error", "delay", "drop", "corrupt", "duplicate", "lifecycle",
                "stall");
        JsonObject stall = byName("faults", "stall");
        assertThat(stall.getString("shortFor")).isEqualTo("lifecycle action=suspend");
        assertThat(paramNames(stall)).containsExactly("duration", "every", "repeat");
        assertThat(paramNames(byName("faults", "lifecycle"))).containsExactly("action", "duration", "every", "repeat");
        assertThat(paramNames(byName("faults", "error")))
                .containsExactly("after", "body", "exception", "header.<name>", "message", "status");
        assertThat(strings(byName("faults", "lifecycle").get("options"))).containsExactly("expect", "from", "within");
        assertThat(strings(byName("faults", "error").get("hooks"))).containsExactly("send", "reply", "dlq", "poll", "entry",
                "step");
        // duplicate is a fault of the sends only
        assertThat(strings(byName("faults", "duplicate").get("hooks"))).containsExactly("send");
        assertThat(strings(byName("faults", "duplicate").get("targets"))).containsExactly("component", "endpoint");
        assertThat(paramNames(byName("faults", "duplicate"))).isEmpty();
        assertThat(names("options")).containsExactly("at", "expect", "from", "in", "rate", "until", "when",
                "within");
        // the TUI form picks the route of in=
        assertThat(byName("options", "in").getString("pick")).isEqualTo("routes");
    }

    @Test
    void theRecipesHaveUniqueIdsAndTheirPlaceholders() {
        Set<String> ids = new HashSet<>();
        for (Recipe recipe : ChaosModel.recipeList()) {
            assertThat(ids.add(recipe.id())).as("recipe id %s", recipe.id()).isTrue();
            Set<String> used = new LinkedHashSet<>();
            Matcher m = PLACEHOLDER.matcher(recipe.rule());
            while (m.find()) {
                used.add(m.group(1));
            }
            Set<String> declared = new LinkedHashSet<>();
            recipe.placeholders().forEach(p -> declared.add(p.name()));
            assertThat(declared).as("the placeholders of recipe %s", recipe.id()).isEqualTo(used);
        }
    }

    static List<Recipe> recipes() {
        return ChaosModel.recipeList();
    }

    @ParameterizedTest
    @MethodSource("recipes")
    void everyRecipeParsesWithItsExamplesAndDefaults(Recipe recipe) {
        // the examples
        ChaosRuleParser.parse(fill(recipe, false), new DefaultClassResolver(), MAX);
        // the defaults, with the examples where there is no default
        ChaosRuleParser.parse(fill(recipe, true), new DefaultClassResolver(), MAX);
    }

    @Test
    void everyRecipeSaysWhatItUncoversWhenNotToUseItAndWhatToWatch() {
        // CAMEL-26699: the add form guides a person (or an AI) with these
        Map<String, ChaosModel.Guide> guides = ChaosModel.guides();
        for (Recipe recipe : ChaosModel.recipeList()) {
            ChaosModel.Guide guide = guides.get(recipe.id());
            assertThat(guide).as(recipe.id()).isNotNull();
            assertThat(guide.uncovers()).as(recipe.id()).isNotEmpty();
            assertThat(guide.avoid()).as(recipe.id()).isNotEmpty();
            assertThat(guide.watch()).as(recipe.id()).isNotEmpty();
        }
        assertThat(guides.keySet()).containsExactlyInAnyOrderElementsOf(
                ChaosModel.recipeList().stream().map(Recipe::id).toList());
    }

    @Test
    void theModeOfACorruptRecipeOffersEveryMode() {
        Recipe recipe = ChaosModel.recipeList().stream().filter(r -> r.id().equals("corrupt-entry")).findFirst()
                .orElseThrow();
        Placeholder mode = recipe.placeholders().stream().filter(p -> p.name().equals("mode")).findFirst().orElseThrow();
        for (Object value : (JsonArray) mode.pick().get("values")) {
            String rule = fill(recipe, true).replace("mode=headers", "mode=" + value);
            ChaosRuleParser.parse(rule, new DefaultClassResolver(), MAX);
        }
    }

    private static String fill(Recipe recipe, boolean defaults) {
        String rule = recipe.rule();
        for (Placeholder p : recipe.placeholders()) {
            String value = defaults && p.defaultValue() != null ? p.defaultValue() : p.example();
            rule = rule.replace("${" + p.name() + "}", value);
        }
        assertThat(rule).doesNotContain("${");
        return rule;
    }

    private static JsonObject chaos() {
        return (JsonObject) ChaosModel.model().get("chaos");
    }

    private static List<String> names(String key) {
        return ((JsonArray) chaos().get(key)).stream().map(o -> ((JsonObject) o).getString("name")).toList();
    }

    private static JsonObject byName(String key, String name) {
        return (JsonObject) ((JsonArray) chaos().get(key)).stream()
                .filter(o -> name.equals(((JsonObject) o).getString("name"))).findFirst().orElseThrow();
    }

    private static List<String> paramNames(JsonObject fault) {
        return ((JsonArray) fault.get("parameters")).stream().map(o -> ((Map<?, ?>) o).get("name").toString()).toList();
    }

    private static List<String> strings(Object array) {
        return ((List<?>) array).stream().map(Object::toString).toList();
    }

    private static Hook hook(String key) {
        for (Hook h : Hook.values()) {
            if (h.key.equals(key)) {
                return h;
            }
        }
        throw new AssertionError("No hook " + key);
    }

    private static Target target(String key) {
        for (Target t : Target.values()) {
            if (t.key.equals(key)) {
                return t;
            }
        }
        throw new AssertionError("No target " + key);
    }
}
