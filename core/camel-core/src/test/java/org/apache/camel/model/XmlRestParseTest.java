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
package org.apache.camel.model;

import jakarta.xml.bind.JAXBException;

import org.apache.camel.model.rest.GetDefinition;
import org.apache.camel.model.rest.RestContainer;
import org.apache.camel.model.rest.RestDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XmlRestParseTest extends XmlTestSupport {

    @Test
    public void testParseSimpleRestXml() throws Exception {
        RestDefinition rest = assertOneRest("simpleRest.xml");
        assertEquals("/users", rest.getPath());

        assertEquals(1, rest.getVerbs().size());
        GetDefinition get = (GetDefinition) rest.getVerbs().get(0);
        assertEquals("/view/{id}", get.getPath());
        assertEquals("direct:getUser", get.getTo().getUri());
    }

    protected RestDefinition assertOneRest(String uri) throws JAXBException {
        RestContainer context = assertParseRestAsJaxb(uri);
        return assertOneElement(context.getRests());
    }

}
