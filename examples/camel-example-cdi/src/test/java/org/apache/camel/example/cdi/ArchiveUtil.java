package org.apache.camel.example.cdi;

import org.apache.camel.cdi.CdiCamelContext;
import org.apache.camel.cdi.internal.CamelExtension;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

import java.io.File;

/**
 *  Util class used to create Archive used by Arquillian
 */
public class ArchiveUtil {

    @TargetsContainer("weld-ee-embedded-1.1")
    public static Archive<?> createWeldArchive(String[] packages) {

        JavaArchive jar =  ShrinkWrap.create(JavaArchive.class)
                .addPackage(CdiCamelContext.class.getPackage())
                .addPackage(CamelExtension.class.getPackage())
                .addPackages(false, packages)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");

        // System.out.println(jar.toString(true));

        return jar;
    }

    @TargetsContainer("jbossas-managed")
    public static Archive<?> createJBossASArchive(String[] packages) {

        JavaArchive jarTest = ShrinkWrap.create(JavaArchive.class)
                .addPackages(false, packages)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");

        File[] libs = Maven.resolver()
                .loadPomFromFile("pom.xml")
                .resolve("org.apache.camel:camel-core","org.apache.camel:camel-cdi","org.apache.activemq:activemq-camel")
                .withTransitivity()
                .as(File.class);

        return ShrinkWrap
                .create(WebArchive.class, "test.war")
                .addAsLibrary(jarTest)
                .addAsLibraries(libs);
    }

}
