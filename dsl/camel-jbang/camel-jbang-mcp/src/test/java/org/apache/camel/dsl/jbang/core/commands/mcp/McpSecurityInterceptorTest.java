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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.interceptor.InvocationContext;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(McpSecurityInterceptor.stripControlChars("a\u0007b\u0001c"))
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

    @Test
    void overLongArgumentIsRejectedAndTheFileLeftIntact(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "original");
        String content = "a".repeat(McpSecurityConfig.DEFAULT_MAX_ARGUMENT_LENGTH) + "TAIL_MUST_SURVIVE";

        assertThatThrownBy(() -> writeFile(securedTools(false), dir, content))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("'content'")
                .hasMessageContaining(String.valueOf(McpSecurityConfig.DEFAULT_MAX_ARGUMENT_LENGTH))
                .hasMessageContaining("camel_edit_file");
        assertThat(Files.readString(dir.resolve("notes.txt"))).isEqualTo("original");
    }

    @Test
    void argumentWithinTheLimitIsWrittenInFull(@TempDir Path dir) throws Exception {
        String content = "a".repeat(McpSecurityConfig.DEFAULT_MAX_ARGUMENT_LENGTH - 4) + "TAIL";

        writeFile(securedTools(false), dir, content);

        assertThat(Files.readAllBytes(dir.resolve("notes.txt"))).isEqualTo(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void redactedContentIsNotWrittenBack(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "password=hunter2");

        assertThatThrownBy(() -> writeFile(securedTools(true), dir, "password=" + McpSecretRedactor.REDACTED))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining(McpSecretRedactor.REDACTED);
        assertThat(Files.readString(dir.resolve("notes.txt"))).isEqualTo("password=hunter2");
    }

    /** An interceptor with security on, as CDI would build it, around the shared authoring tools. */
    private static McpSecurityInterceptor securedTools(boolean redaction) {
        McpSecurityConfig config = new McpSecurityConfig();
        config.enabled = true;
        config.accessLevel = "admin";
        config.redactionEnabled = redaction;
        config.redactionPatterns = Optional.empty();
        config.maxArgumentLength = McpSecurityConfig.DEFAULT_MAX_ARGUMENT_LENGTH;
        McpSecretRedactor redactor = new McpSecretRedactor();
        redactor.config = config;
        McpSecurityInterceptor interceptor = new McpSecurityInterceptor();
        interceptor.config = config;
        interceptor.auditLogger = new McpAuditLogger();
        interceptor.redactor = redactor;
        return interceptor;
    }

    private static Object writeFile(McpSecurityInterceptor interceptor, Path dir, String content) throws Exception {
        AuthoringTools tools = new AuthoringTools();
        tools.securityConfig = interceptor.config;
        Method method = AuthoringTools.class.getMethod("camel_write_file", String.class, String.class, String.class,
                String.class);
        Object result = interceptor.intercept(new Invocation(tools, method, dir.toString(), "notes.txt", content, null));
        assertThat(((JsonObject) result).getString("status")).isIn("created", "overwritten");
        return result;
    }

    /** The invocation of one tool method, as the CDI container hands it to the interceptor. */
    private static final class Invocation implements InvocationContext {
        private final Object target;
        private final Method method;
        private Object[] parameters;
        private final Map<String, Object> contextData = new HashMap<>();

        Invocation(Object target, Method method, Object... parameters) {
            this.target = target;
            this.method = method;
            this.parameters = parameters;
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Object getTimer() {
            return null;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Constructor<?> getConstructor() {
            return null;
        }

        @Override
        public Object[] getParameters() {
            return parameters;
        }

        @Override
        public void setParameters(Object[] params) {
            this.parameters = params;
        }

        @Override
        public Map<String, Object> getContextData() {
            return contextData;
        }

        @Override
        public Object proceed() throws Exception {
            try {
                return method.invoke(target, parameters);
            } catch (InvocationTargetException e) {
                throw (Exception) e.getCause();
            }
        }
    }
}
