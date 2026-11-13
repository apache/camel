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
package org.apache.camel.util;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.xml.jaxb.JaxbModelToXMLDumper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JAXB dumper writes the doCatch and doFinally of a doTry also when they are set as properties, as the YAML DSL
 * does (CAMEL-25410).
 */
public class JaxbDumpModelAsXmlTryPropertyClausesTest extends ContextTestSupport {

    @Test
    public void testDumpModelAsXml() throws Exception {
        CatchDefinition doCatch = new CatchDefinition();
        doCatch.getExceptions().add("java.lang.IllegalStateException");
        doCatch.addOutput(new ToDefinition("mock:catch"));
        FinallyDefinition doFinally = new FinallyDefinition();
        doFinally.addOutput(new ToDefinition("mock:finally"));
        TryDefinition doTry = new TryDefinition();
        // as YAML with doCatch and doFinally written above the steps
        doTry.setCatchClauses(List.of(doCatch));
        doTry.setFinallyClause(doFinally);
        doTry.addOutput(new ToDefinition("mock:try"));

        RouteDefinition route = new RouteDefinition();
        route.routeId("myRoute");
        route.from("direct:start");
        route.addOutput(doTry);

        String xml = new JaxbModelToXMLDumper().dumpModelAsXml(context, route);
        log.info(xml);

        assertTrue(xml.contains("<doCatch"), xml);
        assertTrue(xml.contains("<doFinally"), xml);
        assertTrue(xml.indexOf("mock:try") < xml.indexOf("mock:catch"), xml);
        assertTrue(xml.indexOf("mock:catch") < xml.indexOf("mock:finally"), xml);
        assertEquals(xml.indexOf("mock:catch"), xml.lastIndexOf("mock:catch"), xml);
    }
}
