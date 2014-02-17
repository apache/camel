<?xml version="1.0"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            queryBinding="xslt2">

    <!-- <sch:ns uri="http://apache.org/xml/xcatalog/example" prefix="art" /> -->

    <!-- Your constraints go here -->
    <sch:pattern>
        <sch:rule context="chapter">
            <sch:assert test="title">A chapter should have a title</sch:assert>
        </sch:rule>
    </sch:pattern>

</sch:schema>