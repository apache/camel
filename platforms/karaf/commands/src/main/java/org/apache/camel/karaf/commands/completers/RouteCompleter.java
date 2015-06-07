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

import jline.console.completer.StringsCompleter;

/**
 * A Jline completer for the Camel routes.
 */
public class RouteCompleter extends CamelCompleterSupport {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public int complete(String buffer, int cursor, List candidates) {
        try {
            StringsCompleter delegate = new StringsCompleter();
            List<Map<String, String>> routes = camelController.getRoutes(null);
            for (Map<String, String> row : routes) {
                delegate.getStrings().add(row.get("routeId"));
            }
            return delegate.complete(buffer, cursor, candidates);
        } catch (Exception e) {
            // nothing to do, no completion
        }
        return 0;
    }

}
