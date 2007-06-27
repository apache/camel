/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jbi;

import junit.framework.TestCase;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.client.DefaultServiceMixClient;

import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessagingException;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;
import java.io.File;
import java.net.URL;
import java.net.URI;

/**
 * @version $Revision: 1.1 $
 */
public class IntegrationTest extends TestCase {
    protected JBIContainer container = new JBIContainer();

    private File tempRootDir;

    /*
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
        container.setCreateMBeanServer(false);
        container.setMonitorInstallationDirectory(false);
        tempRootDir = File.createTempFile("servicemix", "rootDir");
        tempRootDir.delete();
        File tempTemp = new File(tempRootDir.getAbsolutePath() + "/temp");
        if (!tempTemp.mkdirs()) {
            fail("Unable to create temporary working root directory [" + tempTemp.getAbsolutePath() + "]");
        }
        System.out.println("Using temporary root directory [" + tempRootDir.getAbsolutePath() + "]");

        container.setRootDir(tempRootDir.getAbsolutePath());
        container.setMonitorInstallationDirectory(false);
        container.setUseMBeanServer(false);
        container.setCreateMBeanServer(false);
        container.setFlowName("st");
        container.init();
        container.start();
    }

    public void testComponentInstallation() throws Exception {
        CamelJbiComponent component = new CamelJbiComponent();
        container.activateComponent(component, "#ServiceMixComponent#");
        URL url = getClass().getResource("su1-src/camel-context.xml");
        File path = new File(new URI(url.toString()));
        path = path.getParentFile();
        ServiceMixClient client = new DefaultServiceMixClient(container);

        for (int i = 0; i < 2; i++) {
            // Deploy and start su
            component.getServiceUnitManager().deploy("su1", path.getAbsolutePath());
            component.getServiceUnitManager().init("su1", path.getAbsolutePath());
            component.getServiceUnitManager().start("su1");

            // Send message
            InOut inout = client.createInOutExchange();
            ServiceEndpoint endpoint = client.getContext().getEndpoint(CamelJbiEndpoint.SERVICE_NAME, "queue:a");

            //QName serviceQName = new QName("http://servicemix.apache.org/demo/", "chained");
            //QName serviceQName = new QName("queue:a", "endpoint");
            //inout.setService(serviceQName);
            inout.setEndpoint(endpoint);
            client.send(inout);

            // Stop and undeploy
            component.getServiceUnitManager().stop("su1");
            component.getServiceUnitManager().shutDown("su1");
            component.getServiceUnitManager().undeploy("su1", path.getAbsolutePath());

            // Send message
            inout = client.createInOutExchange();
            //inout.setService(serviceQName);
            inout.setEndpoint(endpoint);
            try {
                client.send(inout);
            } catch (MessagingException e) {
                // Ok, the lw component is undeployed
            }

        }
    }

    /*
     * @see TestCase#tearDown()
     */

    protected void tearDown() throws Exception {
        super.tearDown();
        container.stop();
        container.shutDown();
        deleteDir(tempRootDir);
    }

    public static boolean deleteDir(File dir) {
        System.out.println("Deleting directory : " + dir.getAbsolutePath());
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        // The directory is now empty so delete it
        return dir.delete();
    }
}
