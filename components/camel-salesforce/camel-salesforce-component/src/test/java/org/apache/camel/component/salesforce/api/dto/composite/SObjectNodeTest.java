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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

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

        assertEquals("Size of the node should be 2", 2, node.size());

        assertEquals("There should be one child in this node", 1, childrenAry.length);

        assertSame("First record should be smith contact", smith, childrenAry[0].getObject());
    }

    @Test
    public void shouldBeAbleToAddChildObject() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", smith);

        final Stream<SObjectNode> children = node.getChildNodesOfType("Contacts");
        final SObjectNode[] childrenAry = toArray(children);

        assertEquals("Size of the node should be 2", 2, node.size());

        assertEquals("There should be one child in this node", 1, childrenAry.length);

        assertSame("First record should be smith contact", smith, childrenAry[0].getObject());
    }

    @Test
    public void shouldBeAbleToFetchChildNodes() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", new SObjectNode(tree, smith));
        node.addChild("Contacts", new SObjectNode(tree, evans));

        final Stream<SObjectNode> children = node.getChildNodes();
        final SObjectNode[] childrenAry = toArray(children);

        assertEquals("There should be two child records in this node", 2, childrenAry.length);

        assertSame("First record should be smith contact", smith, childrenAry[0].getObject());
        assertSame("Second record should be evans contact", evans, childrenAry[1].getObject());

        assertEquals("Size of the node should be 3", 3, node.size());
    }

    @Test
    public void shouldBeAbleToFetchChildren() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", smith);
        node.addChild("Contacts", evans);

        final Stream<AbstractSObjectBase> children = node.getChildren();
        final Object[] childrenAry = children.toArray();

        assertEquals("There should be two child records in this node", 2, childrenAry.length);

        assertSame("First record should be smith contact", smith, childrenAry[0]);
        assertSame("Second record should be evans contact", evans, childrenAry[1]);

        assertEquals("Size of the node should be 3", 3, node.size());
    }

    @Test
    public void shouldCreateNode() {
        final SObjectTree tree = new SObjectTree();

        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", new SObjectNode(tree, smith));
        node.addChild("Contacts", new SObjectNode(tree, evans));

        assertSame("Object in the node should be the given account", simpleAccount, node.getObject());
        assertEquals("Type of the object in node should be auto-detected", "Account", node.getObjectType());

        final Stream<SObjectNode> children = node.getChildNodesOfType("Contacts");
        final SObjectNode[] childrenAry = toArray(children);

        assertEquals("There should be two records in this node", 2, childrenAry.length);

        assertSame("First record should be smith contact", smith, childrenAry[0].getObject());
        assertEquals("Type of first record should be Contact", "Contact", childrenAry[0].getObjectType());

        assertSame("Second record should be evans contact", evans, childrenAry[1].getObject());
        assertEquals("Type of second record should be Contact", "Contact", childrenAry[1].getObjectType());

        assertEquals("Size of the node should be 3", 3, node.size());
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

        assertEquals("There should be two records in this node", 2, childrenAry.length);

        assertSame("First record should be smith contact", smith, childrenAry[0].getObject());
        assertSame("Second record should be evans contact", evans, childrenAry[1].getObject());

        assertEquals("Size of the node should be 3", 3, node.size());
    }

    @Test
    public void shouldFetchChildrenOfType() {
        final SObjectTree tree = new SObjectTree();
        final SObjectNode node = new SObjectNode(tree, simpleAccount);
        node.addChild("Contacts", smith);
        node.addChild("Contacts", evans);

        final Stream<AbstractSObjectBase> children = node.getChildrenOfType("Contacts");
        final Object[] childrenAry = children.toArray();

        assertEquals("There should be two child records in this node", 2, childrenAry.length);

        assertSame("First record should be smith contact", smith, childrenAry[0]);
        assertSame("Second record should be evans contact", evans, childrenAry[1]);

        assertEquals("Size of the node should be 3", 3, node.size());
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

        assertEquals("There should be four records in this node", 4, childrenAry.length);

        assertSame("First record should be smith contact", smith, childrenAry[0]);
        assertSame("Second record should be evans contact", evans, childrenAry[1]);
        assertSame("Third record should be bond contact", bond, childrenAry[2]);
        assertSame("Fourth record should be moneypeny contact", moneypenny, childrenAry[3]);

        assertEquals("Size of the node should be 5", 5, node.size());
    }

    @Test
    public void typeOfShouldBeBasedOnSimpleClassName() {
        assertEquals("Type of Account should be 'Account'", "Account", SObjectNode.typeOf(new Account()));
        assertEquals("Type of Contact should be 'Contact'", "Contact", SObjectNode.typeOf(new Contact()));
    }
}
