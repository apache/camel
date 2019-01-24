/**
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

import javax.xml.bind.JAXBException;

import org.apache.camel.model.rest.GetVerbDefinition;
import org.apache.camel.model.rest.RestContainer;
import org.apache.camel.model.rest.RestDefinition;
import org.junit.Test;

public class XmlRestParseToDTest extends XmlTestSupport {

    @Test
    public void testParseSimpleRestXml() throws Exception {
        RestDefinition rest = assertOneRest("simpleRestToD.xml");
        assertEquals("/users", rest.getPath());

        assertEquals(1, rest.getVerbs().size());
        GetVerbDefinition get = (GetVerbDefinition) rest.getVerbs().get(0);
        assertEquals("/view/{id}", get.getUri());
        assertEquals("bean:getUser?id=${header.id}", get.getToD().getUri());
    }

    protected RestDefinition assertOneRest(String uri) throws JAXBException {
        RestContainer context = assertParseRestAsJaxb(uri);
        RestDefinition rest = assertOneElement(context.getRests());
        return rest;
    }

}
