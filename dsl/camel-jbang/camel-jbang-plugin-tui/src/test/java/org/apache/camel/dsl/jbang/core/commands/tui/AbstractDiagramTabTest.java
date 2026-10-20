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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The route diagram keys the Diagram and Routes tabs share: they behave the same in both tabs. The Routes tab used to
 * keep the detail panel open when going back to the topology.
 */
class AbstractDiagramTabTest {

    private final MonitorContext ctx = new MonitorContext(
            new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));

    static Stream<Function<MonitorContext, AbstractDiagramTab>> tabs() {
        return Stream.of(DiagramTab::new, RoutesTab::new);
    }

    /** A tab showing the diagram of route r1 with its detail panel, reached from r0. */
    private AbstractDiagramTab inRoute(Function<MonitorContext, AbstractDiagramTab> factory, boolean fromRoute) {
        AbstractDiagramTab tab = factory.apply(ctx);
        tab.diagram.setLoadingPlaceholder();
        tab.topologyMode = false;
        tab.drillDownRouteId = "r1";
        tab.detailMode = true;
        if (fromRoute) {
            tab.routeNavigationStack.push("r0");
        }
        return tab;
    }

    @ParameterizedTest
    @MethodSource("tabs")
    void tJumpsBackToTheTopologyAndClosesTheDetailPanel(Function<MonitorContext, AbstractDiagramTab> factory) {
        AbstractDiagramTab tab = inRoute(factory, true);

        assertThat(tab.handleKeyEvent(KeyEvent.ofChar('t'))).isTrue();

        assertThat(tab.topologyMode).isTrue();
        assertThat(tab.detailMode).isFalse();
        assertThat(tab.drillDownRouteId).isNull();
        assertThat(tab.routeNavigationStack).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("tabs")
    void escWalksBackAlongTheRoutesFollowedThenToTheTopology(Function<MonitorContext, AbstractDiagramTab> factory) {
        AbstractDiagramTab tab = inRoute(factory, true);

        assertThat(tab.handleEscape()).isTrue();
        assertThat(tab.topologyMode).isFalse();
        assertThat(tab.drillDownRouteId).isEqualTo("r0");
        assertThat(tab.detailMode).as("still on a route: the panel stays").isTrue();

        assertThat(tab.handleEscape()).isTrue();
        assertThat(tab.topologyMode).isTrue();
        assertThat(tab.detailMode).isFalse();
    }

    @ParameterizedTest
    @MethodSource("tabs")
    void dTogglesTheDetailPanelOnlyWithNodesToShow(Function<MonitorContext, AbstractDiagramTab> factory) {
        AbstractDiagramTab tab = inRoute(factory, false);
        tab.detailMode = false;

        // the route has no nodes laid out yet: d does nothing for the diagram
        tab.handleKeyEvent(KeyEvent.ofChar('d'));
        assertThat(tab.detailMode).isFalse();
    }

    @ParameterizedTest
    @MethodSource("tabs")
    void yShowsTheBodyTypesOnTheArrowsOfARoute(Function<MonitorContext, AbstractDiagramTab> factory) {
        AbstractDiagramTab tab = inRoute(factory, false);

        assertThat(tab.handleKeyEvent(KeyEvent.ofChar('y'))).isTrue();
        assertThat(tab.diagram.isShowBodyTypes()).isTrue();
        assertThat(tab.handleKeyEvent(KeyEvent.ofChar('y'))).isTrue();
        assertThat(tab.diagram.isShowBodyTypes()).isFalse();

        // the topology has no steps to show them for
        tab.topologyMode = true;
        tab.handleKeyEvent(KeyEvent.ofChar('y'));
        assertThat(tab.diagram.isShowBodyTypes()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("tabs")
    void mAndEToggleWhatTheDiagramShows(Function<MonitorContext, AbstractDiagramTab> factory) {
        AbstractDiagramTab tab = factory.apply(ctx);
        tab.diagram.setLoadingPlaceholder();

        assertThat(tab.handleKeyEvent(KeyEvent.ofChar('m'))).isTrue();
        assertThat(tab.diagramMetrics).isFalse();
        assertThat(tab.handleKeyEvent(KeyEvent.ofChar('e'))).isTrue();
        assertThat(tab.externalMode).isEqualTo(1);
    }
}
