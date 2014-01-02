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

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.naming.Context;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.jackrabbit.core.TransientRepository;
import org.junit.Before;

/**
 * JcrRouteTestSupport
 * 
 * @version $Id$
 */
public abstract class JcrRouteTestSupport extends CamelTestSupport {

    private Repository repository;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/repository_with_auth");
        deleteDirectory("target/repository");
        super.setUp();
    }

    protected Repository getRepository() {
        return repository;
    }

    protected Session openSession() throws RepositoryException {
        return getRepository().login(new SimpleCredentials("user", "pass".toCharArray()));
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        repository = new TransientRepository("target/repository.xml", "target/repository");
        context.bind("repository", repository);
        return context;
    }
}
