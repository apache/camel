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
package org.apache.camel.karaf.commands;

import org.apache.camel.commands.RouteProfileCommand;
import org.apache.camel.commands.StringEscape;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;

@Command(scope = "camel", name = "route-profile", description = "Display profile information about Camel route(s).")
@Service
public class RouteProfile extends AbstractRouteCommand {

    private StringEscape stringEscape = org.apache.camel.karaf.commands.internal.StringEscape.getInstance();

    @Override
    public Object execute() throws Exception {
        RouteProfileCommand command = new RouteProfileCommand(route, context);
        command.setStringEscape(stringEscape);
        return command.execute(this, System.out, System.err);
    }
}
