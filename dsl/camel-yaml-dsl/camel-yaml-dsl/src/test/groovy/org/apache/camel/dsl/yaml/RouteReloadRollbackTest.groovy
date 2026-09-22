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
package org.apache.camel.dsl.yaml

import org.apache.camel.ServiceStatus
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.spi.Resource
import org.apache.camel.support.ResourceHelper
import org.apache.camel.support.RouteWatcherReloadStrategy

import java.nio.file.Files
import java.nio.file.Path

/**
 * CAMEL-24860: a reload that fails (a route file saved with a mistake) restores the routes that ran before, instead
 * of leaving the application without routes until the next successful save. CAMEL-24899: a project whose routes are
 * all in one file goes back to the content that last loaded, which is kept in memory.
 * <p>
 * The mistake the tests save is always the same one, and it is a real one (CAMEL-24850): the endpoint of pollEnrich
 * is an expression, so {@code pollEnrich: {uri: "file:./order.json"}} has no uri property to bind and the loader
 * rejects the file with "pollEnrich: unsupported field: uri". It is the right kind of mistake here because it fails
 * while the routes are built, not while the YAML is parsed, which is what a reload has to survive. The form that
 * works is {@code pollEnrich: {expression: {constant: {expression: "file:./order.json"}}}}.
 */
class RouteReloadRollbackTest extends YamlTestSupport {

    Path dir
    Path good
    Path bad

    @Override
    def doSetup() {
        dir = Files.createTempDirectory("camel-reload")
        good = dir.resolve("good.camel.yaml")
        bad = dir.resolve("bad.camel.yaml")
        Files.writeString(good, '''
            - route:
                id: good
                from:
                  uri: direct:good
                  steps:
                    - to:
                        uri: mock:good
            ''')
        Files.writeString(bad, '''
            - route:
                id: bad
                from:
                  uri: direct:bad
                  steps:
                    - to:
                        uri: mock:bad
            ''')
        context.start()
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + good), ResourceHelper.resolveResource(context, "file:" + bad))
    }

    def cleanup() {
        dir.toFile().deleteDir()
    }

    def 'the only route file keeps its previous version when the save is broken'() {
        setup:
            def solo = Files.createTempDirectory("camel-reload-solo")
            def only = solo.resolve("only.camel.yaml")
            Files.writeString(only, """
                - route:
                    id: only
                    from:
                      uri: direct:only
                      steps:
                        - to:
                            uri: mock:only
                """)
            def context2 = new org.apache.camel.impl.DefaultCamelContext()
            context2.start()
            org.apache.camel.support.PluginHelper.getRoutesLoader(context2)
                    .loadRoutes(ResourceHelper.resolveResource(context2, "file:" + only))
            def strategy = new RouteWatcherReloadStrategy(solo.toString())
            strategy.setCamelContext(context2)
            strategy.setPattern("*.yaml")
            strategy.doStart()
            // one successful reload, so the content that runs is remembered
            strategy.getResourceReload().onReload(only.toString(), ResourceHelper.resolveResource(context2, "file:" + only))
            assert context2.getRouteController().getRouteStatus("only") == ServiceStatus.Started
        when: 'the only route file is saved with a mistake (pollEnrich takes an expression, not a uri)'
            Files.writeString(only, """
                - route:
                    id: only
                    from:
                      uri: direct:only
                      steps:
                        - pollEnrich:
                            uri: file:./order.json
                """)
            def failure = null
            try {
                strategy.getResourceReload().onReload(only.toString(), ResourceHelper.resolveResource(context2, "file:" + only))
            } catch (Exception e) {
                failure = e
            }
        then: 'the reload fails and the version that ran before is still running'
            failure != null
            context2.getRouteController().getRouteStatus("only") == ServiceStatus.Started
        when: 'the file is fixed'
            Files.writeString(only, """
                - route:
                    id: only
                    from:
                      uri: direct:only
                      steps:
                        - to:
                            uri: mock:fixed
                """)
            strategy.getResourceReload().onReload(only.toString(), ResourceHelper.resolveResource(context2, "file:" + only))
        then: 'the fixed version runs, not the remembered one'
            context2.getRouteController().getRouteStatus("only") == ServiceStatus.Started
            context2.getRoute("only").getEndpoint().getEndpointUri().startsWith("direct://only")
            context2.getRoutes().size() == 1
        cleanup:
            strategy.doStop()
            context2.stop()
            solo.toFile().deleteDir()
    }

    def 'everything removed forgets the remembered content, a deleted file is not put back'() {
        setup:
            def gone = Files.createTempDirectory("camel-reload-gone")
            def file = gone.resolve("gone.camel.yaml")
            Files.writeString(file, """
                - route:
                    id: gone
                    from:
                      uri: direct:gone
                      steps:
                        - to:
                            uri: mock:gone
                """)
            def ctx = new org.apache.camel.impl.DefaultCamelContext()
            ctx.start()
            def strategy = new RouteWatcherReloadStrategy(gone.toString())
            strategy.setCamelContext(ctx)
            strategy.setPattern("*.yaml")
            strategy.doStart()
            strategy.getResourceReload().onReload(file.toString(), ResourceHelper.resolveResource(ctx, "file:" + file))
            assert ctx.getRouteController().getRouteStatus("gone") == ServiceStatus.Started
        when: 'every route file is removed (the on-demand strategy asks for that when the directory is empty)'
            strategy.onRouteReload(null, true)
        then: 'no routes run'
            ctx.routes.isEmpty()
        when: 'the file comes back with a mistake (pollEnrich takes an expression, not a uri)'
            Files.writeString(file, """
                - route:
                    id: gone
                    from:
                      uri: direct:gone
                      steps:
                        - pollEnrich:
                            uri: file:./order.json
                """)
            def failure = null
            try {
                strategy.getResourceReload().onReload(file.toString(), ResourceHelper.resolveResource(ctx, "file:" + file))
            } catch (Exception e) {
                failure = e
            }
        then: 'the reload fails and the removed route is not put back from memory'
            failure != null
            ctx.routes.isEmpty()
        cleanup:
            strategy.doStop()
            ctx.stop()
            gone.toFile().deleteDir()
    }

    def 'a failed reload restores the previous routes'() {
        setup:
            def strategy = new RouteWatcherReloadStrategy(dir.toString())
            strategy.setCamelContext(context)
            strategy.setPattern("*.yaml")
            // the strategy is not started (no file watcher in the test): its reload callback is driven by hand
            strategy.doStart()
            assert context.getRouteController().getRouteStatus("good") == ServiceStatus.Started
            assert context.getRouteController().getRouteStatus("bad") == ServiceStatus.Started
        when: 'the second file is saved with a mistake (pollEnrich takes an expression, not a uri)'
            Files.writeString(bad, '''
                - route:
                    id: bad
                    from:
                      uri: direct:bad
                      steps:
                        - pollEnrich:
                            uri: file:./order.json
                ''')
            def failure = null
            try {
                strategy.getResourceReload().onReload(bad.toString(), ResourceHelper.resolveResource(context, "file:" + bad))
            } catch (Exception e) {
                failure = e
            }
        then: 'the reload fails, and the route of the other file runs again'
            failure != null
            context.getRouteController().getRouteStatus("good") == ServiceStatus.Started
            context.getRoute("bad") == null
        when: 'the file is fixed'
            Files.writeString(bad, '''
                - route:
                    id: bad
                    from:
                      uri: direct:bad
                      steps:
                        - to:
                            uri: mock:bad
                ''')
            strategy.getResourceReload().onReload(bad.toString(), ResourceHelper.resolveResource(context, "file:" + bad))
        then: 'both run'
            context.getRouteController().getRouteStatus("good") == ServiceStatus.Started
            context.getRouteController().getRouteStatus("bad") == ServiceStatus.Started
        cleanup:
            strategy.doStop()
    }
}
