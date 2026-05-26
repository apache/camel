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

public class TracerIncludePatternsTest {

    @Test
    void shouldReturnTrueWhenEndpointMatchesIncludePattern() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:foo,direct:bar");

            boolean result = tracer.include("direct:foo", ctx);

            assertTrue(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldReturnFalseWhenEndpointDoesNotMatchIncludePattern() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:foo,direct:bar");

            boolean result = tracer.include("direct:baz", ctx);

            assertFalse(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldTrimIncludePatternsBeforeMatching() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("  direct:foo  ,  direct:bar ");

            boolean result = tracer.include("direct:bar", ctx);

            assertTrue(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldReturnFalseWhenEndpointUriIsNull() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:foo");

            boolean result = tracer.include(null, ctx);

            assertFalse(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldMatchWildcardIncludePattern() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:*");

            boolean result = tracer.include("direct:test", ctx);

            assertTrue(result);

        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldNotMatchWildcardIncludePatternWithDifferentPrefix() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:*");

            boolean result = tracer.include("seda:test", ctx);

            assertFalse(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldMatchMultipleWildcardIncludePatterns() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:*,seda:*");

            assertTrue(tracer.include("direct:test", ctx));
            assertTrue(tracer.include("seda:test", ctx));
            assertFalse(tracer.include("vm:test", ctx));
        } catch (Exception e) {
            fail(e);
        }
    }
}
