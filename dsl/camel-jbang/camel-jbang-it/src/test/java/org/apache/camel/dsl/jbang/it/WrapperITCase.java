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
package org.apache.camel.dsl.jbang.it;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@Tag("container-only")
@DisabledOnOs(WINDOWS)
public class WrapperITCase extends JBangTestSupport {

    @Test
    public void testWrapper() {
        // wrapper installs camelw/camelw.cmd scripts in a directory; use a temp dir inside the container
        execInContainer(getMainCommand() + " wrapper --dir=/tmp/wrapper-test");
        Assertions.assertThat(execInContainer("ls /tmp/wrapper-test"))
                .as("wrapper should create camelw script")
                .contains("camelw");
    }
}
