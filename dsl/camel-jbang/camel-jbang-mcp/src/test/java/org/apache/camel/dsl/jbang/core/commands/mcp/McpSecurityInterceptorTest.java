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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpSecurityInterceptorTest {

    @Test
    void stripControlCharsRemovesNullBytes() {
        assertThat(McpSecurityInterceptor.stripControlChars("hello\0world"))
                .isEqualTo("helloworld");
    }

    @Test
    void stripControlCharsPreservesNewlinesTabsReturns() {
        assertThat(McpSecurityInterceptor.stripControlChars("line1\nline2\ttab\rreturn"))
                .isEqualTo("line1\nline2\ttab\rreturn");
    }

    @Test
    void stripControlCharsRemovesBellAndOtherControls() {
        assertThat(McpSecurityInterceptor.stripControlChars("abc"))
                .isEqualTo("abc");
    }

    @Test
    void stripControlCharsPreservesNormalText() {
        String normal = "normal text with spaces and symbols!@#$%^&*()";
        assertThat(McpSecurityInterceptor.stripControlChars(normal)).isEqualTo(normal);
    }

    @Test
    void stripControlCharsHandlesNull() {
        assertThat(McpSecurityInterceptor.stripControlChars(null)).isNull();
    }

    @Test
    void stripControlCharsHandlesEmptyString() {
        assertThat(McpSecurityInterceptor.stripControlChars("")).isEmpty();
    }

    @Test
    void accessDenialMessageFormat() {
        McpSecurityConfig.AccessLevel readOnly = McpSecurityConfig.AccessLevel.READ_ONLY;

        assertThat(readOnly.permits(false, true)).isFalse();
        assertThat(readOnly.permits(false, false)).isFalse();
        assertThat(readOnly.permits(true, false)).isTrue();
    }
}
