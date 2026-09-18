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
package org.apache.camel.component.rest.postman.support;

import org.apache.camel.component.rest.postman.model.PostmanCollection;
import org.apache.camel.component.rest.postman.model.PostmanItem;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostmanRequestIndexTest {

    private static PostmanRequestIndex index(String json, String filter) throws Exception {
        return PostmanRequestIndex.build(PostmanCollection.parse((JsonObject) Jsoner.deserialize(json)), filter);
    }

    private static String collection(String items) {
        return "{\"info\":{\"name\":\"t\",\"schema\":\"v2.1\"},\"item\":[" + items + "]}";
    }

    private static String request(String name) {
        return "{\"name\":\"" + name + "\",\"request\":{\"method\":\"GET\",\"url\":\"https://h/x\"}}";
    }

    private static String requestWithId(String name, String id) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name
               + "\",\"request\":{\"method\":\"GET\",\"url\":\"https://h/x\"}}";
    }

    private static String folder(String name, String items) {
        return "{\"name\":\"" + name + "\",\"item\":[" + items + "]}";
    }

    @Test
    void shouldResolveByUniqueSlug() throws Exception {
        PostmanRequestIndex index = index(collection(request("Get User By Id")), null);

        PostmanRequestIndex.Selection selection = index.resolve("getUserById");

        assertThat(selection.single()).isTrue();
        assertThat(selection.items()).singleElement()
                .extracting(PostmanItem::getName).isEqualTo("Get User By Id");
    }

    @Test
    void shouldPreferAnExactIdOverASlug() throws Exception {
        String id = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
        PostmanRequestIndex index = index(collection(requestWithId("Get User", id)), null);

        assertThat(index.resolve(id).items()).singleElement()
                .extracting(PostmanItem::getName).isEqualTo("Get User");
    }

    @Test
    void shouldQualifyCollidingSlugsWithTheirFolder() throws Exception {
        PostmanRequestIndex index = index(collection(
                folder("Users", request("Get")) + "," + folder("Pets", request("Get"))), null);

        assertThat(index.getItems()).extracting(PostmanItem::getCanonicalId)
                .containsExactly("users/get", "pets/get");
        assertThat(index.resolve("users/get").items()).singleElement()
                .extracting(PostmanItem::getFolderPath).isEqualTo(java.util.List.of("Users"));
    }

    @Test
    void shouldRejectAnAmbiguousSlug() throws Exception {
        PostmanRequestIndex index = index(collection(
                folder("Users", request("Get")) + "," + folder("Pets", request("Get"))), null);

        assertThatThrownBy(() -> index.resolve("get"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 requests named `get`")
                .hasMessageContaining("users/get")
                .hasMessageContaining("pets/get");
    }

    @Test
    void shouldDisambiguateDuplicateNamesInTheSameFolderWithASuffix() throws Exception {
        PostmanRequestIndex index = index(collection(request("Get") + "," + request("Get")), null);

        assertThat(index.getItems()).extracting(PostmanItem::getCanonicalId)
                .containsExactly("get", "get-2");
    }

    @Test
    void shouldSelectTheWholeCollectionWhenNoSelectorIsGiven() throws Exception {
        PostmanRequestIndex index = index(collection(
                request("A") + "," + folder("Users", request("B") + "," + request("C"))), null);

        PostmanRequestIndex.Selection selection = index.resolve(null);

        assertThat(selection.single()).isFalse();
        assertThat(selection.description()).isEqualTo("the whole collection");
        assertThat(selection.items()).hasSize(3);
    }

    @Test
    void shouldSelectAFolder() throws Exception {
        PostmanRequestIndex index = index(collection(
                request("A") + "," + folder("Users", request("B") + "," + request("C"))), null);

        PostmanRequestIndex.Selection selection = index.resolve("users");

        assertThat(selection.single()).isFalse();
        assertThat(selection.items()).extracting(PostmanItem::getName).containsExactly("B", "C");
    }

    @Test
    void shouldSelectANestedFolderByItsOuterFolder() throws Exception {
        PostmanRequestIndex index = index(collection(
                folder("Api", folder("Users", request("B")) + "," + request("C"))), null);

        assertThat(index.resolve("api").items()).hasSize(2);
        assertThat(index.resolve("api/users").items()).extracting(PostmanItem::getName).containsExactly("B");
    }

    @Test
    void shouldForceAFolderMatchWithATrailingSlash() throws Exception {
        // a folder and a request that slugify to the same thing
        PostmanRequestIndex index = index(collection(
                request("Users") + "," + folder("Users", request("B"))), null);

        assertThat(index.resolve("users").single()).isTrue();
        assertThat(index.resolve("users/").single()).isFalse();
        assertThat(index.resolve("users/").items()).extracting(PostmanItem::getName).containsExactly("B");
    }

    @Test
    void shouldListCandidatesWhenNothingMatches() throws Exception {
        PostmanRequestIndex index = index(collection(request("Get User")), null);

        assertThatThrownBy(() -> index.resolve("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no request or folder `nope`")
                .hasMessageContaining("getUser");
    }

    @Test
    void shouldApplyAnIncludeFilter() throws Exception {
        PostmanRequestIndex index = index(collection(
                folder("Users", request("A")) + "," + folder("Pets", request("B"))), "users/**");

        assertThat(index.getItems()).extracting(PostmanItem::getName).containsExactly("A");
    }

    @Test
    void shouldApplyAnExcludeFilter() throws Exception {
        PostmanRequestIndex index = index(collection(
                folder("Users", request("A")) + "," + folder("Pets", request("B"))), "!pets/**");

        assertThat(index.getItems()).extracting(PostmanItem::getName).containsExactly("A");
    }

    @Test
    void shouldInheritVariablesFromCollectionAndFolder() throws Exception {
        String json = "{\"info\":{\"name\":\"t\",\"schema\":\"v2.1\"},"
                      + "\"variable\":[{\"key\":\"a\",\"value\":\"1\"},{\"key\":\"b\",\"value\":\"root\"}],"
                      + "\"item\":[{\"name\":\"F\",\"variable\":[{\"key\":\"b\",\"value\":\"folder\"}],"
                      + "\"item\":[" + request("R") + "]}]}";

        PostmanItem item = index(json, null).getItems().get(0);

        assertThat(item.getScopeVariables()).containsEntry("a", "1").containsEntry("b", "folder");
    }

    @Test
    void shouldInheritAuthFromTheNearestScope() throws Exception {
        String json = "{\"info\":{\"name\":\"t\",\"schema\":\"v2.1\"},"
                      + "\"auth\":{\"type\":\"bearer\",\"bearer\":[{\"key\":\"token\",\"value\":\"root\"}]},"
                      + "\"item\":[{\"name\":\"F\","
                      + "\"auth\":{\"type\":\"basic\",\"basic\":[{\"key\":\"username\",\"value\":\"u\"}]},"
                      + "\"item\":[" + request("R") + "]}]}";

        PostmanItem item = index(json, null).getItems().get(0);

        assertThat(item.getEffectiveAuth().getType()).isEqualTo("basic");
    }

    @Test
    void shouldSkipItemsThatAreNeitherRequestNorFolder() throws Exception {
        PostmanRequestIndex index = index(collection("{\"name\":\"empty\"}," + request("A")), null);

        assertThat(index.getItems()).extracting(PostmanItem::getName).containsExactly("A");
    }

    @Test
    void shouldExpandTheShorthandStringRequest() throws Exception {
        PostmanRequestIndex index = index(
                collection("{\"name\":\"Ping\",\"request\":\"https://api.example.com/ping\"}"), null);

        PostmanItem item = index.getItems().get(0);
        assertThat(item.getRequest().getMethod()).isEqualTo("GET");
        assertThat(item.getRequest().getUrl().getHost()).isEqualTo("api.example.com");
    }
}
