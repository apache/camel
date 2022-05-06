## Contributing to Apache Camel
First of all, thank you for having an interest in contributing to Apache Camel.
If this is your first contribution to Camel, please review [the guidelines for contributing to Camel and its related projects](https://camel.apache.org/community/contributing/).

Then follow these simple guidelines for working on the code and committing your changes.

- Fork and then clone the GitHub camel repository:

    git clone https://github.com/your-github-username/camel.git

- Build the project using Maven:

    mvn clean install -Pfastinstall,format

Note: the `format` profile will ensure that the code is properly formatted according to the project standards:

- Add a unit test with assertions for your changes.

- Run Checkstyle using the `sourcecheck` profile in Maven:

    mvn clean install -Psourcecheck

- Check the documentation (.adoc file) for the code which you have modified and update it if required. For example if you have added or modified a component, the documentation file is in the src/main/docs folder.

- Write a commit message which references the related JIRA or GitHub issue and includes some detail of your changes.
- Push to your fork and create a pull request.
- Stay engaged, follow and respond to comments or questions you might be asked by the Camel team.

