<?xml version="1.0"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron"
            queryBinding="xslt2">

    <sch:title>Sample Schematron using XPath 2.0</sch:title>
    <sch:ns prefix="xs" uri="http://www.w3.org/2001/XMLSchema"/>
    <sch:ns prefix="p" uri="http://www.apache.org/camel/schematron"/>


    <!-- Your constraints go here -->
    <sch:pattern>
        <sch:rule context="chapter | p:chapter">
            <sch:assert test="title | p:title">A chapter should have a title</sch:assert>
        </sch:rule>
    </sch:pattern>

</sch:schema>