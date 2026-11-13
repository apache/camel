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
package org.apache.camel.component.ai.resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AiResourceRegistryTest {

    private AiResourceRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new AiResourceRegistry();
    }

    @Test
    public void testPutAndGetResource() {
        AiResourceSpec spec = spec("app_config", "camel:///config/app.json");

        registry.put("crm", spec);

        assertThat(registry.getResources().get("crm"))
                .as("Resources registered under 'crm' tag")
                .isNotNull()
                .hasSize(1)
                .contains(spec);
    }

    @Test
    public void testRemoveResourceDropsEmptyTag() {
        AiResourceSpec spec = spec("app_config", "camel:///config/app.json");

        registry.put("crm", spec);
        registry.remove("crm", spec);

        assertThat(registry.getResources().get("crm"))
                .as("Tag entry should be removed when the last resource is removed")
                .isNull();
    }

    @Test
    public void testDuplicateUriUnderSameTagIsRejected() {
        registry.put("crm", spec("app_config", "camel:///config/app.json"));

        assertThatThrownBy(() -> registry.put("crm", spec("other_name", "camel:///config/app.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate resource uri");
    }

    @Test
    public void testSameUriUnderDifferentTagsIsAllowed() {
        AiResourceSpec spec = spec("app_config", "camel:///config/app.json");

        registry.put("crm", spec);
        registry.put("ops", spec);

        assertThat(registry.getAllResources()).hasSize(1);
        assertThat(registry.getResources()).containsOnlyKeys("crm", "ops");
    }

    @Test
    public void testDefaultPoolIsSeparateFromTaggedResources() {
        AiResourceSpec tagged = spec("tagged", "camel:///tagged");
        AiResourceSpec untagged = spec("untagged", "camel:///untagged");

        registry.put("crm", tagged);
        registry.putDefault(untagged);

        assertThat(registry.getDefaultResources()).containsExactly(untagged);
        assertThat(registry.getResources().get("crm")).containsExactly(tagged);
        assertThat(registry.getResourcesByTag("crm"))
                .as("Reading by tag merges the default pool")
                .containsExactlyInAnyOrder(tagged, untagged);
    }

    @Test
    public void testListenerReceivesRegistrationEvents() {
        List<String> events = new ArrayList<>();
        registry.addListener(new AiResourceRegistryListener() {
            @Override
            public void resourceRegistered(String tag, AiResourceSpec spec) {
                events.add("registered:" + tag + ":" + spec.getUri());
            }

            @Override
            public void resourceDeregistered(String tag, AiResourceSpec spec) {
                events.add("deregistered:" + tag + ":" + spec.getUri());
            }
        });

        AiResourceSpec spec = spec("app_config", "camel:///config/app.json");
        registry.put("crm", spec);
        registry.remove("crm", spec);
        registry.putDefault(spec);
        registry.removeDefault(spec);

        assertThat(events).containsExactly(
                "registered:crm:camel:///config/app.json",
                "deregistered:crm:camel:///config/app.json",
                "registered:null:camel:///config/app.json",
                "deregistered:null:camel:///config/app.json");
    }

    @Test
    public void testFailingListenerDoesNotBreakRegistration() {
        registry.addListener(new AiResourceRegistryListener() {
            @Override
            public void resourceRegistered(String tag, AiResourceSpec spec) {
                throw new IllegalStateException("listener blew up");
            }

            @Override
            public void resourceDeregistered(String tag, AiResourceSpec spec) {
            }
        });

        AiResourceSpec spec = spec("app_config", "camel:///config/app.json");
        registry.put("crm", spec);

        assertThat(registry.getResources().get("crm")).containsExactly(spec);
    }

    @Test
    public void testTemplateSpecEqualityAndHashCode() {
        AiResourceTemplateSpec first = template("customer", "crm://customers/{id}");
        AiResourceTemplateSpec same = template("customer", "crm://customers/{id}");
        AiResourceTemplateSpec different = template("order", "crm://orders/{id}");

        assertThat(first)
                .isEqualTo(same)
                .hasSameHashCodeAs(same)
                .isNotEqualTo(different);

        assertThat(new HashSet<>(List.of(first, same)))
                .as("Equal template specs must behave consistently in hash-based collections")
                .hasSize(1);
    }

    @Test
    public void testTemplateRegistrationAndLookup() {
        AiResourceTemplateSpec spec = template("customer", "crm://customers/{id}");

        registry.putTemplate("crm", spec);

        assertThat(registry.getResourceTemplates().get("crm")).containsExactly(spec);
        assertThat(registry.getResourceTemplatesByTag("crm")).containsExactly(spec);
        assertThat(spec.match("crm://customers/42")).hasValue(Map.of("id", "42"));
    }

    @Test
    public void testDuplicateTemplateUriIsRejectedWithinEachPool() {
        registry.putTemplate("crm", template("customer", "crm://customers/{id}"));
        assertThatThrownBy(() -> registry.putTemplate("crm", template("other", "crm://customers/{id}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate resource uriTemplate");

        registry.putDefaultTemplate(template("default_customer", "crm://defaults/{id}"));
        assertThatThrownBy(() -> registry.putDefaultTemplate(template("other_default", "crm://defaults/{id}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate resource uriTemplate");
    }

    @Test
    public void testOverlappingTemplatesAreRejectedWithinSameTag() {
        AiResourceTemplateSpec byId = template("customer", "crm://customers/{id}");
        AiResourceTemplateSpec byNumber = template("byNumber", "crm://customers/{number}");

        registry.putTemplate("crm", byId);

        assertThatThrownBy(() -> registry.putTemplate("crm", byNumber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlapping resource uriTemplate");

        assertThat(registry.getResourceTemplatesByTag("crm")).containsExactly(byId);
    }

    @Test
    public void testOverlappingDefaultAndTaggedTemplatesAreRetainedForAdapterSelection() {
        AiResourceTemplateSpec tagged = template("customer", "crm://customers/{id}");
        AiResourceTemplateSpec untagged = template("byNumber", "crm://customers/{number}");

        registry.putTemplate("crm", tagged);
        registry.putDefaultTemplate(untagged);

        assertThat(registry.getResourceTemplatesByTag("crm")).containsExactlyInAnyOrder(tagged, untagged);
    }

    @Test
    public void testOverlappingTemplatesAreRejectedAcrossTags() {
        AiResourceTemplateSpec byId = template("customer", "crm://customers/{id}");
        AiResourceTemplateSpec byNumber = template("byNumber", "crm://customers/{number}");

        registry.putTemplate("crm", byId);

        assertThatThrownBy(() -> registry.putTemplate("ops", byNumber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlapping resource uriTemplate");

        assertThat(registry.getResourceTemplates()).containsOnlyKeys("crm");
    }

    @Test
    public void testTemplateSnapshotHasDeterministicTagOrder() {
        registry.putTemplate("ops", template("order", "crm://orders/{id}"));
        registry.putTemplate("crm", template("customer", "crm://customers/{id}"));

        assertThat(new ArrayList<>(registry.getResourceTemplates().keySet()))
                .containsExactly("crm", "ops");
    }

    @Test
    public void testTemplateRemovalAndDefaultPool() {
        AiResourceTemplateSpec spec = template("customer", "crm://customers/{id}");
        registry.putDefaultTemplate(spec);
        assertThat(registry.getResourceTemplatesByTag("crm")).containsExactly(spec);
        registry.removeDefaultTemplate(spec);
        assertThat(registry.getAllResourceTemplates()).isEmpty();
    }

    private static AiResourceSpec spec(String name, String uri) {
        return new AiResourceSpec(name, uri, "desc", "text/plain", null, null);
    }

    private static AiResourceTemplateSpec template(String name, String uriTemplate) {
        return new AiResourceTemplateSpec(name, uriTemplate, "desc", "text/plain", null, null);
    }
}
