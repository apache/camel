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

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * CAMEL-26817: the files a file route took, which it moved to .camel, are put back, so the route takes the same files
 * again and an experiment can be run again with the same messages.
 */
class ChaosReplayTest extends ChaosTestSupport {

    private static final File BASE = new File("target/chaos-replay");

    @BeforeEach
    void files() throws Exception {
        FileUtil.removeDir(BASE);
        new File(BASE, "orders").mkdirs();
        // written under a hidden name, which the routes do not take, and then renamed
        write("inbox/a.txt", "A");
        write("inbox/b.txt", "B");
        write("kept/c.txt", "C");
    }

    private static void write(String name, String body) throws Exception {
        File file = new File(BASE, name);
        file.getParentFile().mkdirs();
        File hidden = new File(file.getParentFile(), "." + file.getName());
        Files.writeString(hidden.toPath(), body);
        Files.move(hidden.toPath(), file.toPath());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:target/chaos-replay/inbox?initialDelay=0&delay=50").routeId("inbox").to("mock:inbox")
                        .to("seda:ship");
                from("seda:ship").routeId("ship").to("file:target/chaos-replay/archive");
                from("file:target/chaos-replay/orders?move=done/${file:name}&initialDelay=0&delay=50").routeId("orders")
                        .to("mock:orders");
                from("file:target/chaos-replay/kept?noop=true&initialDelay=0&delay=50").routeId("kept").to("mock:kept");
            }
        };
    }

    @Test
    void theFilesTheRouteTookArePutBack() throws Exception {
        MockEndpoint inbox = getMockEndpoint("mock:inbox");
        inbox.expectedBodiesReceivedInAnyOrder("A", "B");
        MockEndpoint.assertIsSatisfied(context);
        File done = new File(BASE, "inbox/.camel");
        await().atMost(10, TimeUnit.SECONDS).until(() -> ChaosAdvisor.takenFiles(done).size() == 2);

        // what to try says how: the files it took, put back
        ChaosAdvisor.Feed feed = ChaosAdvisor.feed(context.getRoute("inbox"));
        assertThat(feed.replay()).isEqualTo(done.getAbsolutePath());
        assertThat(feed.files()).isEqualTo(2);
        assertThat(feed.hint()).contains("the 2 files the route took")
                .endsWith(": an experiment with --feed=inbox --body=replay:");

        inbox.reset();
        inbox.expectedBodiesReceivedInAnyOrder("A", "B");
        Map<String, Object> replayed = chaos().replayFiles("inbox");
        assertThat(replayed).containsEntry("route", "inbox").containsEntry("files", 2);
        MockEndpoint.assertIsSatisfied(context);

        // moved over the files there: the same two, so a replay is the same each time
        await().atMost(10, TimeUnit.SECONDS).until(() -> new File(BASE, "inbox").listFiles(File::isFile).length == 0);
        assertThat(ChaosAdvisor.takenFiles(done)).extracting(File::getName).containsExactly("a.txt", "b.txt");
    }

    @Test
    void theFolderOfTheMoveOption() throws Exception {
        MockEndpoint orders = getMockEndpoint("mock:orders");
        orders.expectedMessageCount(0);
        assertThat(ChaosAdvisor.feed(context.getRoute("orders")).replay()).isNull();
        assertThatThrownBy(() -> chaos().replayFiles("orders")).hasMessageContaining("has taken no files yet");

        write("orders/1.json", "{}");
        orders.reset();
        orders.expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context);
        File done = new File(BASE, "orders/done");
        await().atMost(10, TimeUnit.SECONDS).until(() -> ChaosAdvisor.takenFiles(done).size() == 1);
        assertThat(ChaosAdvisor.feed(context.getRoute("orders")).replay()).isEqualTo(done.getAbsolutePath());

        orders.reset();
        orders.expectedMessageCount(1);
        assertThat(chaos().replayFiles("orders")).containsEntry("files", 1);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void aRouteThatLeavesItsFilesIsNotReplayed() throws Exception {
        getMockEndpoint("mock:kept").expectedBodiesReceived("C");
        MockEndpoint.assertIsSatisfied(context);
        // noop: the route never takes a file of the same name again
        assertThat(ChaosAdvisor.feed(context.getRoute("kept")).replay()).isNull();
        assertThatThrownBy(() -> chaos().replayFiles("kept")).hasMessageContaining("does not take files it moves");
        assertThatThrownBy(() -> chaos().replayFiles("nope")).hasMessageContaining("There is no route nope");
    }

    @Test
    void theSuggestionsSayHowToReplay() throws Exception {
        getMockEndpoint("mock:inbox").expectedMessageCount(2);
        MockEndpoint.assertIsSatisfied(context);
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> ChaosAdvisor.takenFiles(new File(BASE, "inbox/.camel")).size() == 2);

        List<Object> list = (JsonArray) chaos().suggest().get("suggestions");
        JsonObject feed = list.stream().map(JsonObject.class::cast).filter(s -> "inbox".equals(s.getString("route")))
                .map(s -> (JsonObject) s.get("feed")).findFirst().orElseThrow();
        assertThat(feed.getString("replay")).endsWith(".camel");
        assertThat(feed.getInteger("files")).isEqualTo(2);
        assertThat(feed.getString("replayRoute")).isEqualTo("inbox");
        JsonObject ship = list.stream().map(JsonObject.class::cast).filter(s -> "ship".equals(s.getString("route")))
                .map(s -> (JsonObject) s.get("feed")).findFirst().orElseThrow();
        // the route the file route sends to gets the files too
        assertThat(ship.getString("hint")).startsWith("route inbox sends to it: the 2 files the route took");
        assertThat(ship.getString("replayRoute")).isEqualTo("inbox");
        // a route the file route sends to gets the files too
        assertThat(ChaosAdvisor.replayVia(context)).containsEntry("inbox", "inbox").containsEntry("ship", "inbox")
                .doesNotContainKey("kept");
    }
}
