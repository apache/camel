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
package org.apache.camel.dsl.yaml;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26594: a value under parameters: with an & is kept whole in the endpoint uri, as it is put in RAW (as a secret
 * option is). Before, the value was cut at the &.
 */
class ParametersAmpersandTest extends YamlTestSupport {

    private static final String DIR = "target/data/ParametersAmpersandTest";

    @Override
    public void doSetup() throws Exception {
        FileUtil.removeDir(new File(DIR));
    }

    @Test
    void fileFilterWithAmpersand() throws Exception {
        files(DIR + "/in", "R&D-plan.txt", "other.txt");
        loadRoutes("""
                - from:
                    uri: file:target/data/ParametersAmpersandTest/in
                    parameters:
                      filterFile: "${file:name} contains 'R&D'"
                      noop: true
                      initialDelay: 0
                      delay: 10
                    steps:
                      - to: mock:result
                """);

        assertThat(fromUri()).isEqualTo("file:target/data/ParametersAmpersandTest/in"
                                        + "?filterFile=RAW(${file:name} contains 'R&D')&noop=true&initialDelay=0&delay=10");
        assertConsumed("R&D-plan.txt");
    }

    @Test
    void fileFilterWithAndAndWhenTheUriIsTheScheme() throws Exception {
        files(DIR + "/in", "R&D-plan.txt", "Report.csv", "other.txt");
        loadRoutes("""
                - from:
                    uri: file
                    parameters:
                      directoryName: target/data/ParametersAmpersandTest/in
                      filterFile: "${file:name} contains 'R' && ${file:name} ends with '.txt'"
                      noop: true
                      initialDelay: 0
                      delay: 10
                    steps:
                      - to: mock:result
                """);

        assertConsumed("R&D-plan.txt");
    }

    @Test
    void pathParameterWithAmpersandIsNotInRaw() throws Exception {
        files(DIR + "/R&D", "R&D-plan.txt", "other.txt");
        loadRoutes("""
                - from:
                    uri: file
                    parameters:
                      directoryName: target/data/ParametersAmpersandTest/R&D
                      filterFile: "${file:name} contains 'R&D'"
                      noop: true
                      initialDelay: 0
                      delay: 10
                    steps:
                      - to: mock:result
                """);

        // an & does no harm in the path, and RAW would be taken as part of the directory name
        assertThat(fromUri()).startsWith("file:target/data/ParametersAmpersandTest/R&D?filterFile=RAW(");
        assertConsumed("R&D-plan.txt");
    }

    @Test
    void kameletParameterWithAmpersand() throws Exception {
        // a Kamelet is lenient, so before the value was cut at the & with no error, and the Kamelet got R
        assertKamelet("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: kamelet:greet
                          parameters:
                            message: "R&D"
                      - to: mock:result
                """);
    }

    @Test
    void kameletEipParameterWithAmpersand() throws Exception {
        assertKamelet("""
                - from:
                    uri: direct:start
                    steps:
                      - kamelet:
                          name: greet
                          parameters:
                            message: "R&D"
                      - to: mock:result
                """);
    }

    @Test
    void plusIsReadAsBeforeInAValueWithoutAmpersand() throws Exception {
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: file:target/data/ParametersAmpersandTest/out
                          parameters:
                            include: "R&D+.*"
                            exclude: "draft+copy"
                """);
        context.start();

        assertThat(toUri(0)).isEqualTo("file:target/data/ParametersAmpersandTest/out?include=RAW(R&D+.*)&exclude=draft+copy");
        FileEndpoint file = endpoint(FileEndpoint.class);
        // a value in RAW is taken as it is written
        assertThat(file.getInclude()).isEqualTo("R&D+.*");
        // in a value without & a + is a space, as before (such as in a cron expression written with +)
        assertThat(file.getExclude()).isEqualTo("draft copy");
    }

    @Test
    void placeholderInValueWithAmpersand() throws Exception {
        context.getPropertiesComponent().addInitialProperty("team", "Ops");
        context.getPropertiesComponent().addInitialProperty("dept", "R&D");
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: log:foo
                          parameters:
                            marker: "{{team}} & {{dept}}"
                """);
        context.start();

        // the placeholders are resolved in RAW, so an & in the value of a placeholder is kept as well
        assertThat(toUri(0)).isEqualTo("log:foo?marker=RAW({{team}} & {{dept}})");
        assertThat(endpoint(LogEndpoint.class).getMarker()).isEqualTo("Ops & R&D");
    }

    @Test
    void beanReferenceIsKept() throws Exception {
        GenericFileFilter<File> filter = file -> true;
        context.getRegistry().bind("myFilter", filter);
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: file:target/data/ParametersAmpersandTest/out
                          parameters:
                            filter: "#myFilter"
                            include: "R&D.*"
                """);
        context.start();

        assertThat(toUri(0)).isEqualTo("file:target/data/ParametersAmpersandTest/out?filter=#myFilter&include=RAW(R&D.*)");
        FileEndpoint file = endpoint(FileEndpoint.class);
        assertThat(file.getFilter()).isSameAs(filter);
        assertThat(file.getInclude()).isEqualTo("R&D.*");
    }

    @Test
    void valueInRawIsKept() throws Exception {
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: file:target/data/ParametersAmpersandTest/out
                          parameters:
                            include: "RAW(R&D.*)"
                            exclude: "RAW{draft&copy}"
                            antInclude: "**/R&D/**"
                            schedulerProperties:
                              team: "RAW(R&D)"
                """);
        context.start();

        assertThat(toUri(0)).isEqualTo("file:target/data/ParametersAmpersandTest/out"
                                       + "?include=RAW(R&D.*)&exclude=RAW{draft&copy}&antInclude=RAW(**/R&D/**)"
                                       + "&scheduler.team=RAW(R&D)");
        FileEndpoint file = endpoint(FileEndpoint.class);
        assertThat(file.getInclude()).isEqualTo("R&D.*");
        assertThat(file.getExclude()).isEqualTo("draft&copy");
        assertThat(file.getAntInclude()).isEqualTo("**/R&D/**");
        // a value of a map option is added as it is, so an & in it must be in RAW as written
        assertThat(file.getSchedulerProperties()).containsEntry("team", "R&D");
    }

    @Test
    void toDWithHeaderAndAmpersand() throws Exception {
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - toD:
                          uri: file:target/data/ParametersAmpersandTest/out
                          parameters:
                            fileName: "${header.dept} & ${header.team}.txt"
                """);
        context.start();
        context.createProducerTemplate().sendBodyAndHeaders("direct:start", "plan", Map.of("dept", "R&D", "team", "Ops"));

        // Simple is evaluated in RAW, and the & from the header is kept as well
        assertThat(Path.of(DIR, "out", "R&D & Ops.txt")).hasContent("plan");
    }

    @Test
    void rawForm() throws Exception {
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: log:a
                          parameters:
                            marker: "f(x) && g(y)"
                      - to:
                          uri: log:b
                          parameters:
                            marker: "f(x)&g(y)"
                      - to:
                          uri: log:c
                          parameters:
                            marker: "{x})&y"
                      - to:
                          uri: log:d
                          parameters:
                            marker: "(x)&{y}&z"
                """);

        // RAW(value) can have a ) that is not followed by &
        assertThat(toUri(0)).isEqualTo("log:a?marker=RAW(f(x) && g(y))");
        assertThat(context.getEndpoint(toUri(0), LogEndpoint.class).getMarker()).isEqualTo("f(x) && g(y)");
        // )& would end RAW(value) too soon, so it is RAW{value}
        assertThat(toUri(1)).isEqualTo("log:b?marker=RAW{f(x)&g(y)}");
        assertThat(context.getEndpoint(toUri(1), LogEndpoint.class).getMarker()).isEqualTo("f(x)&g(y)");
        // no RAW form can keep these values whole, so they are as before:
        // RAW{{ is the start of a property placeholder, and both )& and }& would end the value too soon
        assertThat(toUri(2)).isEqualTo("log:c?marker={x})&y");
        assertThat(toUri(3)).isEqualTo("log:d?marker=(x)&{y}&z");
    }

    @Test
    void everyEndpointWithParametersAndDumpRoundTrip() throws Exception {
        loadRoutes("""
                - route:
                    id: rd
                    from:
                      uri: direct:start
                      steps:
                        - wireTap:
                            uri: log:tap
                            parameters:
                              marker: "R&D"
                        - poll:
                            uri: file:target/data/ParametersAmpersandTest/in
                            parameters:
                              include: "R&D.*"
                        - switch:
                            selector:
                              header:
                                expression: dept
                            case:
                              - value: rd
                                uri: log:rd
                                parameters:
                                  marker: "R&D"
                            otherwise:
                              uri: log
                              parameters:
                                loggerName: other
                                marker: "R&D"
                """);

        RouteDefinition route = context.getRouteDefinition("rd");
        assertUris(route);

        // the dump has the value in RAW, so it is loaded as the same uri, which is not put in RAW again
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route, false, true, false, false);
        try (DefaultCamelContext restored = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(restored).loadRoutes(ResourceHelper.fromString("rd.yaml", yaml));
            assertUris(restored.getRouteDefinition("rd"));
        }
    }

    private static void assertUris(RouteDefinition route) {
        assertThat(((WireTapDefinition<?>) route.getOutputs().get(0)).getUri()).isEqualTo("log:tap?marker=RAW(R&D)");
        assertThat(((PollDefinition) route.getOutputs().get(1)).getEndpointUri())
                .isEqualTo("file:target/data/ParametersAmpersandTest/in?include=RAW(R&D.*)");
        SwitchDefinition sw = (SwitchDefinition) route.getOutputs().get(2);
        assertThat(sw.getCases().get(0).getUri()).isEqualTo("log:rd?marker=RAW(R&D)");
        assertThat(sw.getOtherwise().getUri()).isEqualTo("log:other?marker=RAW(R&D)");
    }

    private static void files(String dir, String... names) throws Exception {
        Files.createDirectories(Path.of(dir));
        for (String name : names) {
            Files.writeString(Path.of(dir, name), name);
        }
    }

    private String fromUri() {
        return context.getRouteDefinitions().get(0).getInput().getEndpointUri();
    }

    private String toUri(int index) {
        return ((ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(index)).getEndpointUri();
    }

    private <T extends Endpoint> T endpoint(Class<T> type) {
        return context.getEndpoints().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private void assertKamelet(String route) throws Exception {
        addTemplate("greet", t -> t.from("kamelet:source").setBody().constant("{{message}}"));
        loadRoutes(route);

        withMock("mock:result", mock -> mock.expectedBodiesReceived("R&D"));
        context.start();
        withTemplate(t -> t.to("direct:start").withBody("hello").send());
        MockEndpoint.assertIsSatisfied(context);
    }

    private void assertConsumed(String... names) throws Exception {
        MockEndpoint mock = withMock("mock:result",
                m -> m.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME, (Object[]) names));
        // wait a little to see that no other file is consumed
        mock.setAssertPeriod(500);
        context.start();
        mock.assertIsSatisfied();
    }
}
