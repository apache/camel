/**
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
package org.apache.camel.karaf.commands.completers;

import java.util.List;
import java.util.Map;

import org.apache.camel.karaf.commands.internal.CamelControllerImpl;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;

/**
 * A completer for the Camel routes.
 */
@Service
public class RouteCompleter extends CamelControllerImpl implements Completer {

    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        try {
            StringsCompleter delegate = new StringsCompleter();
            List<Map<String, String>> routes = getRoutes(null);
            for (Map<String, String> row : routes) {
                delegate.getStrings().add(row.get("routeId"));
            }
            return delegate.complete(session, commandLine, candidates);
        } catch (Exception e) {
            // nothing to do, no completion
        }
        return 0;
    }

}
