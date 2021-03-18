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

package org.apache.camel.component.box;

import com.box.sdk.BoxCollaboration;
import com.box.sdk.BoxUser;
import org.apache.camel.component.box.api.BoxCollaborationsManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoxCollaborationsManagerTest {

    @Test
    public void testAddFolderCollaborationNullFolderId() {
        BoxCollaborationsManager bcm = new BoxCollaborationsManager(null);

        BoxUser user = new BoxUser(null, "id");

        assertThrows(IllegalArgumentException.class,
                () -> bcm.addFolderCollaboration(null, user, BoxCollaboration.Role.EDITOR));

        assertThrows(IllegalArgumentException.class,
                () -> bcm.addFolderCollaboration("123", null, BoxCollaboration.Role.EDITOR));

        assertThrows(IllegalArgumentException.class,
                () -> bcm.addFolderCollaboration("123", user, null));
    }
}
