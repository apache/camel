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
package org.apache.camel.component.jcr;

import java.io.File;

import javax.jcr.Repository;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.naming.Context;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.TransientRepository;
import org.junit.Before;

/**
 * Base class for tests that use authentication/authorization in the repository.
 * Ensures that the transient repo is set up properly for each test.
 */
public abstract class JcrAuthTestBase extends CamelTestSupport {

    protected static final String BASE_REPO_PATH = "/home/test";

    protected static final String REPO_PATH = "target/repository";

    private Repository repository;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory(REPO_PATH);
        super.setUp();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        
        repository = new TransientRepository(new File(REPO_PATH));

        // set up a user to authenticate
        SessionImpl session = (SessionImpl) repository
                .login(new SimpleCredentials("admin", "admin".toCharArray()));
        UserManager userManager = session.getUserManager();
        User user = (User) userManager.getAuthorizable("test");
        if (user == null) {
            user = userManager.createUser("test", "quatloos");
        }
        // set up permissions
        String path = session.getRootNode().getPath();
        AccessControlManager accessControlManager = session
                .getAccessControlManager();
        AccessControlPolicyIterator acls = accessControlManager
                .getApplicablePolicies(path);
        AccessControlList acl = null;
        if (acls.hasNext()) {
            acl = (AccessControlList) acls.nextAccessControlPolicy();
        } else {
            acl = (AccessControlList) accessControlManager.getPolicies(path)[0];
        }
        acl.addAccessControlEntry(user.getPrincipal(), accessControlManager.getSupportedPrivileges(path));
        accessControlManager.setPolicy(path, acl);

        session.save();
        session.logout();

        context.bind("repository", repository);
        return context;
    }

    protected Repository getRepository() {
        return repository;
    }

}