# camel-format-plugin

The plugin is used to reformat the source code at build time.  It repackages 
the `net.revelc.code.formatter:formatter-maven-plugin`, adding a way to exclude 
portions of the source file from being formatted.  This is useful to avoid 
reformating inside the DSL which is usually indented with semantics.

The only modified file is the `JavaFormatter` class. The two other `FormatterMojo` 
and `ValidateMojo` classes are provided so that the maven plugin plugin can easily detect 
the goals. 
