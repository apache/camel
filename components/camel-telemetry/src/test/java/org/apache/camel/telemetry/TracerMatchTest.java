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

public class TracerMatchTest {

    @Test
    void shouldReturnTrueWhenIncludedAndNotExcluded() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:*");
            tracer.setExcludePatterns("direct:admin");

            assertTrue(tracer.match("direct:orders", ctx));
            assertFalse(tracer.match("direct:admin", ctx));
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldReturnFalseWhenEndpointIsNotIncluded() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:*");

            boolean result = tracer.match("seda:test", ctx);

            assertFalse(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldReturnTrueWhenNoIncludePatternsAndNotExcluded() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setExcludePatterns("direct:admin");

            assertTrue(tracer.match("seda:test", ctx));
            assertFalse(tracer.match("direct:admin", ctx));
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldReturnFalseWhenEndpointUriIsNull() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:*");
            tracer.setExcludePatterns("direct:admin");

            boolean result = tracer.match(null, ctx);

            assertFalse(result);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldSupportMultipleIncludeAndExcludePatterns() {
        try (CamelContext ctx = new DefaultCamelContext();
             Tracer tracer = new MockTracer()) {

            tracer.setIncludePatterns("direct:*,seda:*");
            tracer.setExcludePatterns("direct:admin,seda:internal");

            assertTrue(tracer.match("direct:test", ctx));
            assertTrue(tracer.match("seda:orders", ctx));

            assertFalse(tracer.match("direct:admin", ctx));
            assertFalse(tracer.match("seda:internal", ctx));
        } catch (Exception e) {
            fail(e);
        }
    }
}
