Box.com OSGi Example
====================

A simple example which sets up a camel route to upload files
found in an `inbox` directory to a box.com account.

You will need to edit the `src/main/resources/META-INF/spring/camel-context.xml`
file and enter in your box.com credentials.

Then you will need to compile the example:
  mvn install

To run the example on Apache ServiceMix 4.x or Apache Karaf 2.2.x

1) launch karaf
  
2) Add features required
features:addUrl mvn:org.apache.camel.karaf/apache-camel/${version}/xml/features
features:install camel-spring
features:install camel-box
  
3) Deploy the example
osgi:install -s mvn:org.apache.camel/camel-example-cxf-osgi/${version}

4) Copy files to the inbox directory.

5) Watch them get transferred to your box.com account.

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel Riders!
