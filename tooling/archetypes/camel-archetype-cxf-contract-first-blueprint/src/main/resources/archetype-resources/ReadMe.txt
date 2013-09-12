Camel Router Project for Apache CXF contract-first using Blueprint (OSGi)
=========================================================================

To build this project use

    mvn install

To deploy the project in OSGi. For example using Apache ServiceMix
or Apache Karaf. You need to install the following features first:

    features:install camel-jaxb
    features:install camel-cxf

And then you can install this example from its shell:

    osgi:install -s mvn:${groupId}/${artifactId}/${version}

The web services from Apache CXF is usually listed at:

    http://localhost:8181/cxf

And the WSDL file for this example at:

    http://localhost:8181/cxf/report/?wsdl

For more help see the Apache Camel documentation

    http://camel.apache.org/

