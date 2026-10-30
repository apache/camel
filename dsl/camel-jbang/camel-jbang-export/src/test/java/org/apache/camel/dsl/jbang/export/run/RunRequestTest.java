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
package org.apache.camel.dsl.jbang.export.run;

import java.nio.file.Path;

import org.apache.camel.dsl.jbang.export.ExportRequest;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunRequestTest {

    @Test
    void aRunHasTheDefaultsOfCamelRun() {
        RunRequest request = new RunRequest();

        assertThat(request.name).isEqualTo("CamelJBang");
        assertThat(request.runtime).isEqualTo(RuntimeType.jbang);
        assertThat(request.lazyBean).isFalse();
        assertThat(request.javaVersion).isNull();
        assertThat(request.springBootVersion).isNull();
        assertThat(request.quarkusPlatform).isNull();
        assertThat(request.modeline).isTrue();
    }

    @Test
    void theRunOfAnExportHasTheOptionsOfTheExport() {
        ExportRequest export = new ExportRequest();
        export.baseDir = Path.of("src");
        export.files.add("route.camel.yaml");
        export.runtime = RuntimeType.springBoot;
        export.name = "orders";
        export.mavenSettings = "settings.xml";
        export.applicationProperties = new String[] { "foo=bar" };

        RunRequest request = new RunRequest(export);

        assertThat(request.baseDir).isEqualTo(Path.of("src"));
        assertThat(request.runtime).isEqualTo(RuntimeType.springBoot);
        assertThat(request.name).isEqualTo("orders");
        assertThat(request.mavenSettings).isEqualTo("settings.xml");
        assertThat(request.applicationProperties).containsExactly("foo=bar");
        assertThat(request.javaVersion).isEqualTo(export.javaVersion);
        // the run completes the lists of the export
        assertThat(request.files).isSameAs(export.files);
        assertThat(request.dependencies).isSameAs(export.dependencies);
        // what only a run has is the default of camel run
        assertThat(request.modeline).isTrue();
        assertThat(request.exportRun).isFalse();
    }

    @Test
    void theSilentRunOfAnExportHasNoGav() {
        ExportRequest export = new ExportRequest();
        export.gav = "com.acme:orders:1.0";

        RunRequest request = SilentRunDependencyResolver.toRunRequest(export);

        assertThat(request.exportRun).isTrue();
        assertThat(request.gav).isNull();
        assertThat(request.profile).isEqualTo("dev");
        assertThat(request.baseDir).isEqualTo(Path.of("."));
        assertThat(export.gav).isEqualTo("com.acme:orders:1.0");
    }
}
