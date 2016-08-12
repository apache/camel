<sch:rule context="title" xmlns:sch="http://purl.oclc.org/dsdl/schematron">
    <sch:assert test="string-length(.) >= 2">A title should be at least two characters</sch:assert>
</sch:rule>