<?xml version="1.0"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron"
            queryBinding="xslt2">

    <sch:title>Sample Schematron using XPath 2.0</sch:title>
    <sch:ns prefix="p" uri="http://www.apache.org/camel/schematron"/>
    <sch:ns prefix="xs" uri="http://www.w3.org/2001/XMLSchema"/>

    <!-- Your constraints go here -->
    <sch:pattern>

        <sch:rule context="p:chapter">
            <sch:let name="numOfTitles" select="count(p:title)"/>
            <sch:assert test="p:title">A chapter should have a title</sch:assert>
            <sch:report test="count(p:title) > 1">
                        'chapter' element has more than one title present
             </sch:report>
        </sch:rule>
    </sch:pattern>

</sch:schema>