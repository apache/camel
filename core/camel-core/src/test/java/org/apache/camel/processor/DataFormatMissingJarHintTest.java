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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A data format whose jar is not on the classpath fails with the artifact to add.
 */
public class DataFormatMissingJarHintTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testModelDataFormatSaysWhatToAdd() {
        String msg = messages(assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").unmarshal().jaxb("com.foo").to("mock:result");
                }
            });
            context.start();
        }));

        assertTrue(msg.contains("Data format 'jaxb' could not be created."), msg);
        assertTrue(msg.contains("(the jaxb data format is in camel-jaxb; add camel-jaxb to the classpath)"), msg);
    }

    @Test
    public void testDataFormatByRefSaysWhatToAdd() {
        String msg = messages(assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").unmarshal().custom("jaxb").to("mock:result");
                }
            });
            context.start();
        }));

        assertTrue(msg.contains("Cannot find data format in registry with ref: jaxb (the jaxb data format is in camel-jaxb;"),
                msg);
    }

    @Test
    public void testDataFormatByRefThatIsNotBuiltInHasNoHint() {
        String msg = messages(assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").unmarshal().custom("myFormat").to("mock:result");
                }
            });
            context.start();
        }));

        assertTrue(msg.contains("Cannot find data format in registry with ref: myFormat\n"), msg);
    }

    private static String messages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
