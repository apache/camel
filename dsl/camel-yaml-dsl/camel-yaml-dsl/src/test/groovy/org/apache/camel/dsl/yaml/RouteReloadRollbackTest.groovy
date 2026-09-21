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
 * of leaving the application without routes until the next successful save.
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

    def 'a failed reload restores the previous routes'() {
        setup:
            def strategy = new RouteWatcherReloadStrategy(dir.toString())
            strategy.setCamelContext(context)
            strategy.setPattern("*.yaml")
            // the strategy is not started (no file watcher in the test): its reload callback is driven by hand
            strategy.doStart()
            assert context.getRouteController().getRouteStatus("good") == ServiceStatus.Started
            assert context.getRouteController().getRouteStatus("bad") == ServiceStatus.Started
        when: 'the second file is saved with a mistake'
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
