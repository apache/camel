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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.util.stream.Stream;

import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.component.salesforce.dto.generated.Contact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SObjectNodeTest extends CompositeTestBase {

    static SObjectNode[] toArray(final Stream<SObjectNode> children) {
        return children.toArray(l -> new SObjectNode[l]);
    }

    @Test
    public void shouldBeAbleToAddChildNode() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", new SObjectNode(tree, smith));

        final Stream<SObjectNode> children = node.getChildNodesOfType("Contacts");
        final SObjectNode[] childrenAry = toArray(children);

        assertEquals(2, node.size(), "Size of the node should be 2");

        assertEquals(1, childrenAry.length, "There should be one child in this node");

        assertSame(smith, childrenAry[0].getObject(), "First record should be smith contact");
    }

    @Test
    public void shouldBeAbleToAddChildObject() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", smith);

        final Stream<SObjectNode> children = node.getChildNodesOfType("Contacts");
        final SObjectNode[] childrenAry = toArray(children);

        assertEquals(2, node.size(), "Size of the node should be 2");

        assertEquals(1, childrenAry.length, "There should be one child in this node");

        assertSame(smith, childrenAry[0].getObject(), "First record should be smith contact");
    }

    @Test
    public void shouldBeAbleToFetchChildNodes() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", new SObjectNode(tree, smith));
        node.addChild("Contacts", new SObjectNode(tree, evans));

        final Stream<SObjectNode> children = node.getChildNodes();
        final SObjectNode[] childrenAry = toArray(children);

        assertEquals(2, childrenAry.length, "There should be two child records in this node");

        assertSame(smith, childrenAry[0].getObject(), "First record should be smith contact");
        assertSame(evans, childrenAry[1].getObject(), "Second record should be evans contact");

        assertEquals(3, node.size(), "Size of the node should be 3");
    }

    @Test
    public void shouldBeAbleToFetchChildren() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", smith);
        node.addChild("Contacts", evans);

        final Stream<AbstractSObjectBase> children = node.getChildren();
        final Object[] childrenAry = children.toArray();

        assertEquals(2, childrenAry.length, "There should be two child records in this node");

        assertSame(smith, childrenAry[0], "First record should be smith contact");
        assertSame(evans, childrenAry[1], "Second record should be evans contact");

        assertEquals(3, node.size(), "Size of the node should be 3");
    }

    @Test
    public void shouldCreateNode() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", new SObjectNode(tree, smith));
        node.addChild("Contacts", new SObjectNode(tree, evans));

        assertSame(simpleAccount, node.getObject(), "Object in the node should be the given account");
        assertEquals("Account", node.getObjectType(), "Type of the object in node should be auto-detected");

        final Stream<SObjectNode> children = node.getChildNodesOfType("Contacts");
        final SObjectNode[] childrenAry = toArray(children);

        assertEquals(2, childrenAry.length, "There should be two records in this node");

        assertSame(smith, childrenAry[0].getObject(), "First record should be smith contact");
        assertEquals("Contact", childrenAry[0].getObjectType(), "Type of first record should be Contact");

        assertSame(evans, childrenAry[1].getObject(), "Second record should be evans contact");
        assertEquals("Contact", childrenAry[1].getObjectType(), "Type of second record should be Contact");

        assertEquals(3, node.size(), "Size of the node should be 3");
    }

    @Test
    public void shouldCreateNodeWithoutChildRecords() {
        new SObjectNode(new SObjectTree(), simpleAccount);
    }

    @Test
    public void shouldFetchChildrenNodesOfType() {
        final SObjectTree tree = new SObjectTree();
        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", new SObjectNode(tree, smith));
        node.addChild("Contacts", new SObjectNode(tree, evans));

        final Stream<SObjectNode> children = node.getChildNodesOfType("Contacts");
        final SObjectNode[] childrenAry = toArray(children);

        assertEquals(2, childrenAry.length, "There should be two records in this node");

        assertSame(smith, childrenAry[0].getObject(), "First record should be smith contact");
        assertSame(evans, childrenAry[1].getObject(), "Second record should be evans contact");

        assertEquals(3, node.size(), "Size of the node should be 3");
    }

    @Test
    public void shouldFetchChildrenOfType() {
        final SObjectTree tree = new SObjectTree();
        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", smith);
        node.addChild("Contacts", evans);

        final Stream<AbstractSObjectBase> children = node.getChildrenOfType("Contacts");
        final Object[] childrenAry = children.toArray();

        assertEquals(2, childrenAry.length, "There should be two child records in this node");

        assertSame(smith, childrenAry[0], "First record should be smith contact");
        assertSame(evans, childrenAry[1], "Second record should be evans contact");

        assertEquals(3, node.size(), "Size of the node should be 3");
    }

    @Test
    public void shouldSupportAddingDescribedSObjects() {
        final SObjectTree tree = new SObjectTree();
        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild(smith);
        node.addChildren(evans);
        node.addChildren(bond, moneypenny);

        final Stream<AbstractSObjectBase> children = node.getChildrenOfType("Contacts");
        final Object[] childrenAry = children.toArray();

        assertEquals(4, childrenAry.length, "There should be four records in this node");

        assertSame(smith, childrenAry[0], "First record should be smith contact");
        assertSame(evans, childrenAry[1], "Second record should be evans contact");
        assertSame(bond, childrenAry[2], "Third record should be bond contact");
        assertSame(moneypenny, childrenAry[3], "Fourth record should be moneypeny contact");

        assertEquals(5, node.size(), "Size of the node should be 5");
    }

    @Test
    public void typeOfShouldBeBasedOnSimpleClassName() {
        assertEquals("Account", SObjectNode.typeOf(new Account()), "Type of Account should be 'Account'");
        assertEquals("Contact", SObjectNode.typeOf(new Contact()), "Type of Contact should be 'Contact'");
    }
}
