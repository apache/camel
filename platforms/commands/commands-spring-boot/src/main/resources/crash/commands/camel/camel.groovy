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
package crash.commands.camel

import org.apache.camel.commands.*
import org.apache.camel.springboot.commands.crsh.ArgumentCamelContext
import org.apache.camel.springboot.commands.crsh.ArgumentRouteID
import org.apache.camel.springboot.commands.crsh.CamelCommandsFacade
import org.apache.camel.springboot.commands.crsh.CamelCommandsPlugin
import org.crsh.cli.*
import org.crsh.groovy.GroovyCommand

@Usage("Camel related commands")
public class camel extends GroovyCommand {

    private CamelCommandsFacade getCommandsFacade() {
        return context.session["crash"].
                context.getPlugin(CamelCommandsPlugin.class).getCamelCommandsFacade()
    }
    // ===============================
    //    Components and EIP
    // ===============================
    @Command
    @Usage("Lists all Camel components available in the context.")
    @Named("component-list")
    public String component_list(@Required @ArgumentCamelContext String camelContext,
                                 @Usage("Verbose output")
                                 @Option(names = ["v", "verbose"]) Boolean verbose) {
        Boolean v = (null != verbose && Boolean.valueOf(verbose))
        return getCommandsFacade().runCommand(ComponentListCommand.class, camelContext, v)
    }

    @Command
    @Usage("Explains the EIP in the Camel context.")
    @Named("eip-explain")
    public String eip_explain(@Required @ArgumentCamelContext String camelContext,
                              @Required @Argument String nameOrId,
                              @Usage("Verbose output")
                              @Option(names = ["v", "verbose"]) Boolean verbose) {
        Boolean v = (null != verbose && Boolean.valueOf(verbose))
        return getCommandsFacade().runCommand(EipExplainCommand.class, camelContext, nameOrId, v)
    }

    // ===============================
    //    Context
    // ===============================
    @Command
    @Usage("Lists all Camel contexts.")
    @Named("context-list")
    public String context_list() {
        return getCommandsFacade().runCommand(ContextListCommand.class)
    }

    @Command
    @Usage("Displays detailed information about the Camel context.")
    @Named("context-info")
    public String context_info(@Required @ArgumentCamelContext String camelContext,
                               @Usage("Verbose output")
                               @Option(names = ["v", "verbose"]) Boolean verbose) {
        Boolean v = (null != verbose && Boolean.valueOf(verbose))
        return getCommandsFacade().runCommand(ContextInfoCommand.class, camelContext, v)
    }

    @Command
    @Usage("Displays detailed information about the Camel context.")
    @Named("context-inflight")
    public String context_inflight(@Required @ArgumentCamelContext String camelContext,
                                   @Usage("Sort by longest duration")
                                   @Option(names = ["s", "sort"]) Boolean sort,
                                   @Usage("Limit output to number of messages")
                                   @Option(names = ["l", "limit"]) Integer limit) {

        Boolean _sort = (null != sort && Boolean.valueOf(sort))
        Integer _limit = null != limit ? limit : -1;

        if (_limit != -1) {
            out.println("Limiting output to " + _limit + " messages.")
        }

        return getCommandsFacade().runCommand(ContextInflightCommand.class, camelContext, _limit, _sort)
    }

    @Command
    @Usage("Starts the Camel context.")
    @Named("context-start")
    public String context_start(@Required @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(ContextStartCommand.class, camelContext)
    }

    @Command
    @Usage("Stops the Camel context.")
    @Named("context-stop")
    public String context_stop(@Required @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(ContextStopCommand.class, camelContext)
    }

    @Command
    @Usage("Suspends the Camel context.")
    @Named("context-suspend")
    public String context_suspend(@Required @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(ContextSuspendCommand.class, camelContext)
    }

    @Command
    @Usage("Resumes the Camel context.")
    @Named("context-resume")
    public String context_resume(@Required @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(ContextResumeCommand.class, camelContext)
    }

    // ===============================
    //    Endpoints
    // ===============================
    @Command
    @Usage("Lists Camel endpoints.")
    @Named("endpoint-list")
    public String endpoint_list(@Required @ArgumentCamelContext String camelContext,
                                @Usage("Decode URI so they are human readable")
                                @Option(names = ["d", "decode"]) Boolean decode,
                                @Usage("Verbose output")
                                @Option(names = ["v", "verbose"]) Boolean verbose,
                                @Usage("Explain the endpoint options")
                                @Option(names = ["e", "explain"]) Boolean explain) {
        Boolean _verbose = (null != verbose && Boolean.valueOf(verbose))
        Boolean _decode = (null != decode && Boolean.valueOf(decode))
        Boolean _explain = (null != explain && Boolean.valueOf(explain))
        return getCommandsFacade().runCommand(EndpointListCommand.class, camelContext, _decode, _verbose, _explain);
    }

    @Command
    @Usage("Explain all Camel endpoints available in the CamelContext.")
    @Named("endpoint-explain")
    public String endpoint_explain(@Required @ArgumentCamelContext String camelContext,
                                   @Usage("Verbose output")
                                   @Option(names = ["v", "verbose"]) Boolean verbose,
                                   @Usage("Filter endpoint by pattern")
                                   @Option(names = ["f", "filter"]) String filter) {
        Boolean _verbose = (null != verbose && Boolean.valueOf(verbose))
        String _filter = null != filter ? filter : "*";
        return getCommandsFacade().runCommand(EndpointExplainCommand.class, camelContext, _verbose, _filter);
    }

    @Command
    @Usage("Explain all Camel endpoints available in the CamelContext.")
    @Named("endpoint-stats")
    public String endpoint_stats(@Required @ArgumentCamelContext String camelContext,
                                 @Usage("Decode URI so they are human readable")
                                 @Option(names = ["d", "decode"]) Boolean decode,
                                 @Usage("Filter the list by in,out,static,dynamic")
                                 @Option(names = ["f", "filter"]) String filter) {
        Boolean _decode = (null != decode && Boolean.valueOf(decode))
        String[] _filter = filter == null ? [] : filter.split(",")
        return getCommandsFacade().runCommand(EndpointStatisticCommand.class, camelContext, _decode, _filter);
    }

    @Command
    @Usage("Lists all Camel REST services enlisted in the Rest Registry from a CamelContext.")
    @Named("rest-registry-list")
    public String rest_registry_list(@Required @ArgumentCamelContext String camelContext,
                                     @Usage("Decode URI so they are human readable")
                                     @Option(names = ["d", "decode"]) Boolean decode,
                                     @Usage("Verbose output")
                                     @Option(names = ["v", "verbose"]) Boolean verbose) {
        Boolean _verbose = (null != verbose && Boolean.valueOf(verbose))
        Boolean _decode = (null != decode && Boolean.valueOf(decode))
        return getCommandsFacade().runCommand(RestRegistryListCommand.class, camelContext, _decode, _verbose);
    }

    // ===============================
    //    Route
    // ===============================
    @Command
    @Usage("Lists Camel routes for the given Camel context.")
    @Named("route-list")
    public String route_list(@Required @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(RouteListCommand.class, camelContext)
    }

    @Command
    @Usage("Displays information about a Camel route")
    @Named("route-info")
    public String route_info(@Required @ArgumentRouteID String route, @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(RouteInfoCommand.class, route, camelContext)
    }

    @Command
    @Usage("Displays Camel route profile")
    @Named("route-profile")
    public String route_profile(@Required @ArgumentRouteID String route, @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(RouteProfileCommand.class, route, camelContext)
    }

    @Command
    @Usage("Display the Camel route definition in XML.")
    @Named("route-show")
    public String route_show(@Required @ArgumentRouteID String route, @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(RouteShowCommand.class, route, camelContext)
    }

    @Command
    @Usage("Resets route performance stats.")
    @Named("route-reset-stats")
    public String route_reset_stats(
            @Required @ArgumentRouteID String route, @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(RouteResetStatsCommand.class, route, camelContext)
    }

    @Command
    @Usage("Resumes the route operation.")
    @Named("route-resume")
    public String route_resume(@Required @ArgumentRouteID String route, @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(RouteResumeCommand.class, route, camelContext)
    }

    @Command
    @Usage("Suspends the route operation.")
    @Named("route-suspend")
    public String route_suspend(@Required @ArgumentRouteID String route, @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(RouteSuspendCommand.class, route, camelContext)
    }

    @Command
    @Usage("Stops the route operation.")
    @Named("route-stop")
    public String route_stop(@Required @ArgumentRouteID String route, @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(RouteStopCommand.class, route, camelContext)
    }

    @Command
    @Usage("Starts the route operation.")
    @Named("route-start")
    public String route_start(@Required @ArgumentRouteID String route, @ArgumentCamelContext String camelContext) {
        return getCommandsFacade().runCommand(RouteStartCommand.class, route, camelContext)
    }

}

