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
package org.apache.camel.telemetry;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.telemetry.mock.MockTracer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TracerExcludePatternsTest {

    @Test
    void shouldReturnTrueWhenEndpointMatchesPattern() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer();) {
            tracer.setExcludePatterns("direct:foo,direct:bar");

            boolean result = tracer.exclude("direct:foo", ctx);

            assertTrue(result);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    void shouldReturnFalseWhenEndpointDoesNotMatchPattern() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setExcludePatterns("direct:foo,direct:bar");

            boolean result = tracer.exclude("direct:baz", ctx);

            assertFalse(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldTrimPatternsBeforeMatching() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setExcludePatterns("  direct:foo  ,  direct:bar ");

            boolean result = tracer.exclude("direct:bar", ctx);

            assertTrue(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldReturnFalseWhenEndpointUriIsNull() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setExcludePatterns("direct:foo");

            boolean result = tracer.exclude(null, ctx);

            assertFalse(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldSupportWildcardPatterns() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setExcludePatterns("direct:*");

            boolean result = tracer.exclude("direct:test", ctx);

            assertTrue(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldNotMatchWildcardPatternWithDifferentPrefix() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setExcludePatterns("direct:*");

            boolean result = tracer.exclude("seda:orders", ctx);

            assertFalse(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldMatchMultipleWildcardPatterns() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setExcludePatterns("direct:*,seda:*");

            assertTrue(tracer.exclude("direct:test", ctx));
            assertTrue(tracer.exclude("seda:test", ctx));
            assertFalse(tracer.exclude("vm:test", ctx));
        } catch (Exception e) {
            fail(e);
        }
    }
}
