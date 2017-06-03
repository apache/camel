Schematron Component
====================

Schematron is an XML-based language for validating XML instance documents. Schematron is used to make assertions about data in an XML document. Schematron is used to express operational and business rules.
Schematron is an ISO standard.
Use Schematron to verify data interdependencies (co-constraints), check data cardinality, and perform algorithmic checks. A co-constraint is a dependency between data within an XML document or across XML documents. Cardinality refers to the presence or absence of data. An algorithmic check determines data validity by performing an algorithm on the data.

Schematron engine
======================

Schemaron engine is an XSLT based implementation. XLST source code can be found here: https://code.google.com/p/schematron/


Schematron Tutorials
======================

http://www.schematron.com/spec.html

http://www.xml.com/pub/a/2003/11/12/schematron.html

http://www.data2type.de/en/xml-xslt-xslfo/schematron/

http://www.mulberrytech.com/papers/schematron-Philly.pdf

http://www.ldodds.com/papers/schematron_xsltuk.html#c35e2592b6b7

To build this project use

    mvn clean install
