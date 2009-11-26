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
import java.io.IOException;

import javax.jcr.Repository;
import javax.jcr.SimpleCredentials;
import javax.naming.Context;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.jackrabbit.api.jsr283.security.AccessControlManager;
import org.apache.jackrabbit.api.jsr283.security.AccessControlPolicyIterator;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.TransientRepository;
import org.apache.jackrabbit.core.fs.local.FileUtil;
import org.apache.jackrabbit.core.security.authorization.JackrabbitAccessControlList;
import org.junit.Before;

/**
 * Base class for tests that use authentication/authorization in the repository.
 * Ensures that the transient repo is set up properly for each test.
 * 
 * @author Paul Mietz Egli
 * 
 */
public abstract class JcrAuthTestBase extends CamelTestSupport {

    protected static final String BASE_REPO_PATH = "/home/test";

    private static final String CONFIG_FILE = "target/test-classes/repository_with_auth.xml";

    private Repository repository;

    private void clean() throws IOException {
        File[] files = {new File("target/repository_with_auth"),
                        new File("derby.log") };
        for (File file : files) {
            if (file.exists()) {
                FileUtil.delete(file);
            }
        }
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();

        File config = new File(CONFIG_FILE);
        if (!config.exists()) {
            throw new Exception("missing config file: " + config.getPath());
        }
        repository = new TransientRepository(CONFIG_FILE,
                "target/repository_with_auth");

        // set up a user to authenticate
        SessionImpl session = (SessionImpl) repository
                .login(new SimpleCredentials("admin", "admin".toCharArray()));
        UserManager userManager = session.getUserManager();
        User user = (User) userManager.getAuthorizable("test");
        if (user == null) {
            user = userManager.createUser("test", "quatloos");
        }
        // set up permissions
        String permissionsPath = session.getRootNode().getPath();
        AccessControlManager accessControlManager = session
                .getAccessControlManager();
        AccessControlPolicyIterator acls = accessControlManager
                .getApplicablePolicies(permissionsPath);
        if (acls.hasNext()) {
            JackrabbitAccessControlList acl = (JackrabbitAccessControlList) acls
                    .nextAccessControlPolicy();
            acl.addEntry(user.getPrincipal(), accessControlManager
                    .getSupportedPrivileges(permissionsPath), true);
            accessControlManager.setPolicy(permissionsPath, acl);
        } else {
            throw new Exception("could not set access control for path "
                    + permissionsPath);
        }

        session.save();
        session.logout();

        context.bind("repository", repository);
        return context;
    }

    protected Repository getRepository() {
        return repository;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        clean();
        super.setUp();
    }

}