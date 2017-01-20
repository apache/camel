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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.util.Arrays;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.thoughtworks.xstream.XStream;

import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.component.salesforce.dto.generated.Asset;
import org.apache.camel.component.salesforce.dto.generated.Contact;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class SObjectTreeTest extends CompositeTestBase {

    @Test
    public void emptyTreeShouldBeZeroSized() {
        assertEquals(0, new SObjectTree().size());
    }

    @Test
    public void shouldCollectAllObjectTypesInTheTree() {
        final SObjectTree tree = new SObjectTree();
        tree.addObject(new Account()).addChild(new Contact()).addChild("Assets", new Asset());
        tree.addObject(new Account());

        final Class[] types = tree.objectTypes();
        Arrays.sort(types, (final Class l, final Class r) -> l.getName().compareTo(r.getName()));

        assertArrayEquals(new Class[] {Account.class, Asset.class, Contact.class}, types);
    }

    @Test
    public void shouldSerializeToJson() throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

        final ObjectWriter writer = mapper.writerFor(SObjectTree.class);

        final SObjectTree tree = new SObjectTree();

        final SObjectNode account1 = new SObjectNode(tree, simpleAccount);
        account1.addChild("Contacts", smith);
        account1.addChild("Contacts", evans);
        tree.addNode(account1);

        final SObjectNode account2 = new SObjectNode(tree, simpleAccount2);
        tree.addNode(account2);

        final String json = writer.writeValueAsString(tree);

        assertEquals("Should serialize to JSON as in Salesforce example",
            "{\"records\":["//
                + "{"//
                + "\"attributes\":{\"referenceId\":\"ref1\",\"type\":\"Account\"},"//
                + "\"Industry\":\"Banking\","//
                + "\"Name\":\"SampleAccount\","//
                + "\"NumberOfEmployees\":100,"//
                + "\"Phone\":\"1234567890\","//
                + "\"Website\":\"www.salesforce.com\","//
                + "\"Contacts\":{"//
                + "\"records\":["//
                + "{"//
                + "\"attributes\":{\"referenceId\":\"ref2\",\"type\":\"Contact\"},"//
                + "\"Email\":\"sample@salesforce.com\","//
                + "\"LastName\":\"Smith\","//
                + "\"Title\":\"President\""//
                + "},"//
                + "{"//
                + "\"attributes\":{\"referenceId\":\"ref3\",\"type\":\"Contact\"},"//
                + "\"Email\":\"sample@salesforce.com\","//
                + "\"LastName\":\"Evans\","//
                + "\"Title\":\"Vice President\""//
                + "}"//
                + "]"//
                + "}"//
                + "},"//
                + "{"//
                + "\"attributes\":{\"referenceId\":\"ref4\",\"type\":\"Account\"},"//
                + "\"Industry\":\"Banking\","//
                + "\"Name\":\"SampleAccount2\","//
                + "\"NumberOfEmployees\":100,"//
                + "\"Phone\":\"1234567890\","//
                + "\"Website\":\"www.salesforce2.com\""//
                + "}"//
                + "]"//
                + "}",
            json);
    }

    @Test
    public void shouldSerializeToXml() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode account1 = new SObjectNode(tree, simpleAccount);
        account1.addChild("Contacts", smith);
        account1.addChild("Contacts", evans);
        tree.addNode(account1);

        final SObjectNode account2 = new SObjectNode(tree, simpleAccount2);
        tree.addNode(account2);

        final XStream xStream = new XStream();
        xStream.processAnnotations(new Class[] {SObjectTree.class, Account.class, Contact.class, Asset.class});

        final String xml = xStream.toXML(tree);

        assertEquals("Should serialize to XML as in Salesforce example",
            "<SObjectTreeRequest>\n"//
                + "  <records type=\"Account\" referenceId=\"ref1\">\n"//
                + "    <Name>SampleAccount</Name>\n"//
                + "    <Phone>1234567890</Phone>\n"//
                + "    <Website>www.salesforce.com</Website>\n"//
                + "    <Industry>Banking</Industry>\n"//
                + "    <NumberOfEmployees>100</NumberOfEmployees>\n"//
                + "    <Contacts>\n"//
                + "      <records type=\"Contact\" referenceId=\"ref2\">\n"//
                + "        <Email>sample@salesforce.com</Email>\n"//
                + "        <LastName>Smith</LastName>\n"//
                + "        <Title>President</Title>\n"//
                + "      </records>\n"//
                + "      <records type=\"Contact\" referenceId=\"ref3\">\n"//
                + "        <Email>sample@salesforce.com</Email>\n"//
                + "        <LastName>Evans</LastName>\n"//
                + "        <Title>Vice President</Title>\n"//
                + "      </records>\n"//
                + "    </Contacts>\n"//
                + "  </records>\n"//
                + "  <records type=\"Account\" referenceId=\"ref4\">\n"//
                + "    <Name>SampleAccount2</Name>\n"//
                + "    <Phone>1234567890</Phone>\n"//
                + "    <Website>www.salesforce2.com</Website>\n"//
                + "    <Industry>Banking</Industry>\n"//
                + "    <NumberOfEmployees>100</NumberOfEmployees>\n"//
                + "  </records>\n"//
                + "</SObjectTreeRequest>",
            xml);
    }

    @Test
    public void shouldSetIdByReferences() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode account1 = new SObjectNode(tree, simpleAccount);
        account1.addChild("Contacts", smith);
        account1.addChild("Contacts", evans);
        tree.addNode(account1);

        final SObjectNode account2 = new SObjectNode(tree, simpleAccount2);
        tree.addNode(account2);

        tree.setIdFor("ref1", "id1");
        tree.setIdFor("ref4", "id4");
        tree.setIdFor("ref3", "id3");
        tree.setIdFor("ref2", "id2");

        assertEquals("id1", simpleAccount.getId());

        assertEquals("id2", smith.getId());
        assertEquals("id3", evans.getId());

        assertEquals("id4", simpleAccount2.getId());
    }

    @Test
    public void shouldSetIdByReferencesForNestedObjects() {
        final SObjectTree tree = new SObjectTree();

        final Account account = new Account();
        final SObjectNode accountNode = new SObjectNode(tree, account);
        tree.addNode(accountNode);

        final Contact contact = new Contact();
        final SObjectNode contactNode = new SObjectNode(tree, contact);
        accountNode.addChild("Contacts", contactNode);

        final Asset asset = new Asset();
        final SObjectNode assetNode = new SObjectNode(tree, asset);
        contactNode.addChild("Assets", assetNode);

        assertEquals("ref1", accountNode.getAttributes().getReferenceId());
        assertEquals("ref2", contactNode.getAttributes().getReferenceId());
        assertEquals("ref3", assetNode.getAttributes().getReferenceId());

        tree.setIdFor("ref1", "id1");
        tree.setIdFor("ref3", "id3");
        tree.setIdFor("ref2", "id2");

        assertEquals("id1", account.getId());
        assertEquals("id2", contact.getId());
        assertEquals("id3", asset.getId());
    }

    @Test
    public void shouldSetReferences() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode account1 = new SObjectNode(tree, simpleAccount);
        account1.addChild("Contacts", smith);
        account1.addChild("Contacts", evans);
        tree.addNode(account1);

        final SObjectNode account2 = new SObjectNode(tree, simpleAccount2);
        tree.addNode(account2);

        final SObjectNode simpleAccountFromTree = tree.records.get(0);
        assertEquals("ref1", simpleAccountFromTree.getAttributes().getReferenceId());

        final Iterator<SObjectNode> simpleAccountNodes = simpleAccountFromTree.getChildNodes().iterator();
        assertEquals("ref2", simpleAccountNodes.next().getAttributes().getReferenceId());
        assertEquals("ref3", simpleAccountNodes.next().getAttributes().getReferenceId());

        assertEquals("ref4", account2.getAttributes().getReferenceId());
    }

    @Test
    public void shouldSupportBuildingObjectTree() {
        final SObjectTree tree = new SObjectTree();

        tree.addObject(simpleAccount).addChildren("Contacts", smith, evans);

        tree.addObject(simpleAccount2);

        final SObjectNode firstAccountFromTree = tree.records.get(0);
        assertSame(simpleAccount, firstAccountFromTree.getObject());
        assertEquals("Account", firstAccountFromTree.getObjectType());

        final Iterator<SObjectNode> simpleAccountNodes = firstAccountFromTree.getChildNodes().iterator();

        final SObjectNode smithNode = simpleAccountNodes.next();
        assertSame(smith, smithNode.getObject());
        assertEquals("Contact", smithNode.getObjectType());

        final SObjectNode evansNode = simpleAccountNodes.next();
        assertSame(evans, evansNode.getObject());
        assertEquals("Contact", evansNode.getObjectType());

        final SObjectNode secondAccountFromTree = tree.records.get(1);
        assertSame(simpleAccount2, secondAccountFromTree.getObject());
        assertEquals("Account", secondAccountFromTree.getObjectType());
    }

    @Test
    public void treeWithOneNodeShouldHaveSizeOfOne() {
        final SObjectTree tree = new SObjectTree();
        tree.addObject(new Account());

        assertEquals(1, tree.size());
    }

    @Test
    public void treeWithTwoNestedNodesShouldHaveSizeOfTwo() {
        final SObjectTree tree = new SObjectTree();
        final SObjectNode accountNode = tree.addObject(new Account());
        accountNode.addChild("Contacts", new Contact());

        assertEquals(2, tree.size());
    }

    @Test
    public void treeWithTwoNodesShouldHaveSizeOfTwo() {
        final SObjectTree tree = new SObjectTree();
        tree.addObject(new Account());
        tree.addObject(new Account());

        assertEquals(2, tree.size());
    }
}
