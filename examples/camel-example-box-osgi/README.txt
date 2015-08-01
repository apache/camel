Box.com OSGi Example
====================

A simple example which sets up a camel route to upload files
found in an `inbox` directory to a box.com account.

First you will need to compile the example:
  mvn install

To run the example on Apache Karaf 2.3.x

1) launch karaf

2) Add features required
features:addUrl mvn:org.apache.camel.karaf/apache-camel/${version}/xml/features
features:install camel-spring
features:install camel-box

3) Create a `box.properties` file in the Karaf base directory with the following properties
set to your box account credentials.

        box.userName=
        box.userPassword=
        box.clientId=
        box.clientSecret=

3) Deploy the example
osgi:install -s mvn:org.apache.camel/camel-example-box-osgi/${version}

4) Copy files to the `inbox` directory in the Karaf base directory.

5) Watch them get transferred to your box.com account.

Please help us make Apache Camel better - we appreciate any feedback you may
have. Enjoy!

------------------------
The Camel Riders!
