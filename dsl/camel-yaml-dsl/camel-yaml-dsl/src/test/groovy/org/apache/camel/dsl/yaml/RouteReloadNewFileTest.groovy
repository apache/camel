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
import org.apache.camel.support.ResourceHelper
import org.apache.camel.support.RouteWatcherReloadStrategy

import java.nio.file.Files
import java.nio.file.Path

/**
 * CAMEL-24866: a new route file next to a file with several routes reloads without a duplicate route id: the existing
 * file is one source, whatever the number of routes it holds.
 */
class RouteReloadNewFileTest extends YamlTestSupport {

    Path dir
    Path shop

    @Override
    def doSetup() {
        dir = Files.createTempDirectory("camel-reload")
        shop = dir.resolve("shop.camel.yaml")
        Files.writeString(shop, '''
            - route:
                id: orders
                from:
                  uri: direct:orders
                  steps:
                    - to:
                        uri: mock:orders
            - route:
                id: report
                from:
                  uri: direct:report
                  steps:
                    - to:
                        uri: mock:report
            - route:
                id: setup
                from:
                  uri: direct:setup
                  steps:
                    - to:
                        uri: mock:setup
            ''')
        context.start()
        loadRoutes(ResourceHelper.resolveResource(context, "file:" + shop))
    }

    def cleanup() {
        dir.toFile().deleteDir()
    }

    def 'a new file reloads next to a file with several routes'() {
        setup:
            def strategy = new RouteWatcherReloadStrategy(dir.toString())
            strategy.setCamelContext(context)
            strategy.setPattern("*.yaml")
            // the strategy is not started (no file watcher in the test): its reload callback is driven by hand
            strategy.doStart()
            assert context.routes.size() == 3
        when: 'a second file is added'
            def hello = dir.resolve("hello.camel.yaml")
            Files.writeString(hello, '''
                - route:
                    id: hello
                    from:
                      uri: direct:hello
                      steps:
                        - to:
                            uri: mock:hello
                ''')
            strategy.getResourceReload().onReload(hello.toString(), ResourceHelper.resolveResource(context, "file:" + hello))
        then: 'the three routes of the first file and the new one run'
            context.routes.size() == 4
            ["orders", "report", "setup", "hello"].every { context.getRouteController().getRouteStatus(it) == ServiceStatus.Started }
        cleanup:
            strategy.doStop()
    }
}
