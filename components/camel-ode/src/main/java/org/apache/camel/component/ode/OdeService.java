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
package org.apache.camel.component.ode;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;
import javax.xml.namespace.QName;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.ServiceSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.dao.BpelDAOConnectionFactory;
import org.apache.ode.bpel.engine.BpelServerImpl;
import org.apache.ode.bpel.extvar.jdbc.JdbcExternalVariableModule;
import org.apache.ode.bpel.iapi.BindingContext;
import org.apache.ode.bpel.iapi.EndpointReferenceContext;
import org.apache.ode.bpel.iapi.Message;
import org.apache.ode.bpel.iapi.MessageExchange;
import org.apache.ode.bpel.iapi.MessageExchangeContext;
import org.apache.ode.bpel.iapi.MyRoleMessageExchange;
import org.apache.ode.bpel.iapi.ProcessStore;
import org.apache.ode.bpel.memdao.BpelDAOConnectionFactoryImpl;
import org.apache.ode.il.MockScheduler;
import org.apache.ode.il.config.OdeConfigProperties;
import org.apache.ode.il.dbutil.Database;
import org.apache.ode.store.ProcessConfImpl;
import org.apache.ode.store.ProcessStoreImpl;

/**
 * Encapsulates all the stuff needed to setup and use Apache ODE.
 *
 * @version $Revision$
 */
public class OdeService extends ServiceSupport {
    private static final Log LOG = LogFactory.getLog(OdeService.class);

    private static final String CONFIG_FILE_NAME = "camel-ode.properties";
    private final CamelContext camelContext;
    private String installRoot = "./target";
    private String workRoot = "./target";
    private OdeConfigProperties config;
    private BpelServerImpl server;
    private ProcessStore store;
    private MockScheduler scheduler;
    private Database database;
    private DataSource dataSource;
    private TransactionManager transactionManager;
    private BpelDAOConnectionFactory daoConnectionFactory;
    private MessageExchangeContext messageExchangeContext;
    private EndpointReferenceContext endpointReferenceContext;
    private BindingContext bindingContext;

    public OdeService(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    private void initBpelServer() throws Exception {
        config = new OdeConfigProperties(new File(getInstallRoot() + "/" + CONFIG_FILE_NAME), CONFIG_FILE_NAME);
        config.load();

        server = new BpelServerImpl();
        // We don't want the server to automatically load deployed processes, we'll do that explicitly
        endpointReferenceContext = new OdeEndpointReferenceContext();
        messageExchangeContext = new OdeMessageExchangeContext();
        bindingContext = new OdeBindingContext();

        scheduler = new MockScheduler();
        scheduler.setJobProcessor(server);

        // support setting using core/max pool size
//        ExecutorService executorService = Executors.newCachedThreadPool();
//        SimpleScheduler scheduler = new SimpleScheduler(new GUID().toString(), new JdbcDelegate(dataSource), config.getProperties());
//        scheduler.setJobProcessor(server);
//        scheduler.setExecutorService(executorService);
//        scheduler.setTransactionManager(transactionManager);

//        ProcessStoreImpl store = new ProcessStoreImpl(endpointReferenceContext, dataSource, config.getDAOConnectionFactory(), config, false);
        store = new ProcessStoreImpl(endpointReferenceContext, null, "jpa", config, true);
//        registerExternalVariableModules();
//        store.loadAll();


        daoConnectionFactory = new BpelDAOConnectionFactoryImpl(scheduler);

        server.setInMemDaoConnectionFactory(new BpelDAOConnectionFactoryImpl(scheduler, config.getInMemMexTtl()));
        server.setDaoConnectionFactory(daoConnectionFactory);
        server.setEndpointReferenceContext(endpointReferenceContext);
        server.setMessageExchangeContext(messageExchangeContext);
        server.setBindingContext(bindingContext);
        server.setScheduler(scheduler);
        server.setConfigProperties(config.getProperties());
//        server.registerBpelEventListener(new DebugBpelEventListener());

        server.init();
    }

    private void registerExternalVariableModules() {
        JdbcExternalVariableModule jdbcext = new JdbcExternalVariableModule();
        jdbcext.registerDataSource("camel-ode", dataSource);
        server.registerExternalVariableEngine(jdbcext);
    }

    public void deployBpel(String dir) throws Exception {
        String deployxml = dir + "/deploy.xml";
        URL url = camelContext.getClassResolver().loadResourceAsURL(deployxml);
        if (url == null) {
            throw new FileNotFoundException(deployxml);
        }

        File file = new File(url.toURI().getPath()).getParentFile();
        Collection<QName> procs = store.deploy(file);

        for (QName procName : procs) {
            ProcessConfImpl conf = (ProcessConfImpl) store.getProcessConfiguration(procName);
            // Test processes always run with in-mem DAOs
            conf.setTransient(true);
            server.register(conf);
        }
    }

    public String invokeSomething(String name) throws Exception {
        String answer = null;

        String guid = "12345678";
        QName serviceName = new QName("http://ode/bpel/unit-test.wsdl", "HelloService");
        String operation = "hello";

        final Future<MessageExchange.Status> running;
        MyRoleMessageExchange role;
        scheduler.beginTransaction();
        try {
            role = server.getEngine().createMessageExchange(guid, serviceName, operation);
            //role.setProperty("isTwoWay", "true");
            Message msg = role.createMessage(null);

            Document doc = camelContext.getTypeConverter().convertTo(Document.class, "<message><TestPart>" + name + "</TestPart></message>");
            msg.setMessage(doc.getDocumentElement());

            LOG.info("Sending msg " + msg);
            running = role.invoke(msg);
        } finally {
            scheduler.commitTransaction();
        }

        running.get(10000, TimeUnit.MILLISECONDS);

        scheduler.beginTransaction();
        try {
            MessageExchange.Status status = role.getStatus();
            LOG.info("Status: " + status);

            Message response = role.getResponse();
            LOG.info("Response: " + response);
            String xml = camelContext.getTypeConverter().convertTo(String.class, response.getMessage());
            LOG.info("Response XML: " + xml);

            answer = xml;
        } finally {
            scheduler.commitTransaction();
        }

        return answer;
    }

    public String getInstallRoot() {
        return installRoot;
    }

    public void setInstallRoot(String installRoot) {
        this.installRoot = installRoot;
    }

    public String getWorkRoot() {
        return workRoot;
    }

    public void setWorkRoot(String workRoot) {
        this.workRoot = workRoot;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    protected void doStart() throws Exception {
        initBpelServer();
        server.start();
    }

    @Override
    protected void doStop() throws Exception {
        server.stop();
    }

}
