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
package org.apache.camel.component.robotframework;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.robotframework.RobotFramework;

/**
 * Pass camel exchanges to acceptance test written in Robot DSL.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "robotframework", title = "Robot Framework",
             syntax = "robotframework:resourceUri", category = { Category.TESTING },
             headersClass = RobotFrameworkCamelConstants.class)
public class RobotFrameworkEndpoint extends ResourceEndpoint {

    @UriParam
    private RobotFrameworkCamelConfiguration configuration;

    public RobotFrameworkEndpoint(String uri, RobotFrameworkComponent component, String resourceUri,
                                  RobotFrameworkCamelConfiguration configuration) {
        super(uri, component, resourceUri);
        this.configuration = configuration;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "robotframework:" + getResourceUri();
    }

    public RobotFrameworkCamelConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean isAllowContextMapAll() {
        return configuration.isAllowContextMapAll();
    }

    @Override
    public void setAllowContextMapAll(boolean allowContextMapAll) {
        configuration.setAllowContextMapAll(allowContextMapAll);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        // create robot arguments to pass
        RobotFrameworkArguments generatedArguments = new RobotFrameworkArguments();

        generatedArguments.addFileToArguments(configuration.getOutputDirectory(), "-d");
        generatedArguments.addFileToArguments(configuration.getOutput(), "-o");
        generatedArguments.addFileToArguments(configuration.getLog(), "-l");
        generatedArguments.addFileToArguments(configuration.getReport(), "-r");
        generatedArguments.addFileToArguments(configuration.getDebugFile(), "-b");
        generatedArguments.addFileToArguments(configuration.getArgumentFiles(), "-A");
        generatedArguments.addFileToArguments(configuration.getRunFailed(), "-R");

        generatedArguments.addNonEmptyStringToArguments(configuration.getName(), "-N");
        generatedArguments.addNonEmptyStringToArguments(configuration.getDocument(), "-D");
        generatedArguments.addNonEmptyStringToArguments(configuration.getRunMode(), "--runmode");
        generatedArguments.addFlagToArguments(configuration.isDryrun(), "--dryrun");
        generatedArguments.addFlagToArguments(configuration.isExitOnFailure(), "--exitonfailure");
        generatedArguments.addFlagToArguments(configuration.isSkipTeardownOnExit(), "--skipteardownonexit");
        generatedArguments.addNonEmptyStringToArguments(configuration.getRandomize(), "--randomize");
        generatedArguments.addNonEmptyStringToArguments(configuration.getSplitOutputs(), "--splitoutputs");
        generatedArguments.addNonEmptyStringToArguments(configuration.getLogTitle(), "--logtitle");
        generatedArguments.addNonEmptyStringToArguments(configuration.getReportTitle(), "--reporttitle");
        generatedArguments.addNonEmptyStringToArguments(configuration.getReportBackground(), "--reportbackground");
        generatedArguments.addNonEmptyStringToArguments(configuration.getSummaryTitle(), "--summarytitle");
        generatedArguments.addNonEmptyStringToArguments(configuration.getLogLevel(), "-L");
        generatedArguments.addNonEmptyStringToArguments(configuration.getSuiteStatLevel(), "--suitestatlevel");
        generatedArguments.addNonEmptyStringToArguments(configuration.getMonitorWidth(), "--monitorwidth");
        generatedArguments.addNonEmptyStringToArguments(configuration.getMonitorColors(), "--monitorcolors");
        generatedArguments.addNonEmptyStringToArguments(configuration.getListener(), "--listener");

        generatedArguments.addFlagToArguments(configuration.isRunEmptySuite(), "--runemptysuite");
        generatedArguments.addFlagToArguments(configuration.isNoStatusReturnCode(), "--nostatusrc");
        generatedArguments.addFlagToArguments(configuration.isTimestampOutputs(), "-T");
        generatedArguments.addFlagToArguments(configuration.isWarnOnSkippedFiles(), "--warnonskippedfiles");

        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getMetadata() != null ? configuration.getMetadata() : "").split(",")), "-M");
        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getTags() != null ? configuration.getTags() : "").split(",")), "-G");
        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getTests() != null ? configuration.getTests() : "").split(",")), "-t");
        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getSuites() != null ? configuration.getSuites() : "").split(",")), "-s");
        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getIncludes() != null ? configuration.getIncludes() : "").split(",")), "-i");
        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getExcludes() != null ? configuration.getExcludes() : "").split(",")), "-e");
        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getCriticalTags() != null ? configuration.getCriticalTags() : "").split(",")),
                "-c");
        generatedArguments.addListToArguments(
                Arrays.asList(
                        (configuration.getNonCriticalTags() != null ? configuration.getNonCriticalTags() : "").split(",")),
                "-n");

        // create variables from camel exchange to pass into robot
        List<String> variables
                = RobotFrameworkCamelUtils.createRobotVariablesFromCamelExchange(exchange, isAllowContextMapAll());
        exchange.getIn().setHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_VARIABLES, variables);
        generatedArguments.addListToArguments(variables, "-v");

        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getVariableFiles() != null ? configuration.getVariableFiles() : "").split(",")),
                "-V");
        generatedArguments.addListToArguments(
                Arrays.asList(
                        (configuration.getTagStatIncludes() != null ? configuration.getTagStatIncludes() : "").split(",")),
                "--tagstatinclude");
        generatedArguments.addListToArguments(
                Arrays.asList(
                        (configuration.getTagStatExcludes() != null ? configuration.getTagStatExcludes() : "").split(",")),
                "--tagstatexclude");
        generatedArguments.addListToArguments(
                Arrays.asList(
                        (configuration.getCombinedTagStats() != null ? configuration.getCombinedTagStats() : "").split(",")),
                "--tagstatcombine");
        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getTagDocs() != null ? configuration.getTagDocs() : "").split(",")), "--tagdoc");
        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getTagStatLinks() != null ? configuration.getTagStatLinks() : "").split(",")),
                "--tagstatlink");
        generatedArguments.addListToArguments(
                Arrays.asList((configuration.getListeners() != null ? configuration.getListeners() : "").split(",")),
                "--listener");

        // process path and set robot env by that to specify which test cases to
        // run
        // either from a directory or from a file
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");
        log.debug("RobotFrameworkEndpoint resourceUri:{}", path);

        String newResourceUri = null;
        if (getConfiguration().isAllowTemplateFromHeader()) {
            newResourceUri = exchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RESOURCE_URI, String.class);
        }
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RESOURCE_URI);
            log.debug("{} set to {} setting resourceUri to pass robotframework",
                    RobotFrameworkCamelConstants.CAMEL_ROBOT_RESOURCE_URI, newResourceUri);
            path = newResourceUri;
        }

        if (configuration.getXunitFile() == null) {
            configuration.setXunitFile("TEST-" + path.replace(' ', '_') + ".xml");
        }
        generatedArguments.addFileToArguments(configuration.getXunitFile(), "-x");
        generatedArguments.addFlagToArguments(true, "--xunitskipnoncritical");

        generatedArguments.add(path);

        // run robot framework
        int camelRobotReturnCode = RobotFramework.run(generatedArguments.toArray());

        exchange.getIn().setHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE, camelRobotReturnCode);
    }
}
