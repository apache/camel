## Contributing to Apache Camel
First of all, thank you for having an interest in contributing to Apache Camel.
If this is your first contribution to Camel, please review [the guidelines for contributing to Camel and its related projects](https://camel.apache.org/community/contributing/).
For more details about the process of building and testing, please also check the [Contributing section](https://camel.apache.org/manual/latest/contributing.html) in the Documentation on the Apache Camel website.

Then follow these simple guidlines for working on the code.

- Fork and then clone the github camel repository:
    git clone https://github.com/your-github-username*/camel.git

- Build the project using maven:
    mvn clean install -Pfastinstall

- Add a unit test with assertions for your changes

- Run checkstyle using the sourcecheck profile in maven
    mvn clean install -Psourcecheck

- Check the documentation (.adoc file) for the code which you have modified and update it if required. For example if you have added or modified a component, the documentation files are under the [docs/components](https://github.com/apache/camel/tree/main/docs/components/modules/ROOT/pages) folder.

- Write a commit messsage which references the related JIRA or Github issue and includes some detail of your changes
- Push to your fork and create a pull request
- Stay engaged, follow and respond to comments or questions you might be asked.

