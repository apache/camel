=============================================================================
 Camel Spring Integration
=============================================================================

Overview
--------------------------------------------------------------------------
 
 This module provides a nice Spring 2.0 xml extension so that you can 
 build Camel routes in spring with a very consice XML format that is very
 similar the to Camel Fluid api.

 Where in Java you would do a:

   from("seda:a").to("seda:b")

 The spring configuration equivalent would be as follows:

   <route>
     <from uri="seda:a"/>
     <to uri="seda:b"/>
   </route>

Developers
--------------------------------------------------------------------------
 
 As new features are added to the Camel builders, you will need to 
 update the XSD that spring uses to validate the XML syntax.  I think the
 easiest way to do that is to generate the XSD from an example XML file.
 
 I've been using trang for that purpose.  Once installed, you can run:
 
   trang -I xml -O xsd src/test/resources/org/apache/camel/spring/examples.xml src/main/resources/org/apache/camel/spring/generated-camel-spring.xsd

 To generate the XSD, there are some spring specifics mostly in the header
 that trang does not get right, so then what's left is a manual  cut and paste from 
 the generated-camel-spring.xsd to the camel-spring.xsd
