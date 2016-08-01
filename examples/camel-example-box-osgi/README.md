# Box.com OSGi Example

### Introduction

A simple example which sets up a camel route to upload files
found in an `inbox` directory to a box.com account.

#### Camel component used in this example

* camel-code
* camel-bom

### Build

First you will need to compile the example:

	mvn install

### Run

To run the example on Apache Karaf 3.x or newer

#### Step 1

Launch karaf

#### Step 2

Add features required into Karak

	feature:repo-add camel ${version}
	feature:install camel-spring-dm
	feature:install camel-box

#### Step 3

Create a `box.properties` file in the Karaf base directory with the following properties
set to your box account credentials.

        box.userName=
        box.userPassword=
        box.clientId=
        box.clientSecret=

#### Step 4

Deploy the example into Karaf

	install -s mvn:org.apache.camel/camel-example-box-osgi/${version}

#### Step 5

Copy files to the `inbox` directory in the Karaf base directory.

#### Step 6

Watch them get transferred to your box.com account.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
