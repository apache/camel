[ ] Make sure there is a [JIRA issue](https://issues.apache.org/jira/browse/CAMEL) filed for the change (usually before you start working on it).  Trivial changes like typos do not require a JIRA issue.  Your pull request should address just this issue, without pulling in other changes.
[ ] Each commit in the pull request should have a meaningful subject line and body.
[ ] If you're unsure, you can format the pull request title like `[CAMEL-XXX] Fixes bug in camel-file component`, where you replace `CAMEL-XXX` with the appropriate JIRA issue.
[ ] Write a pull request description that is detailed enough to understand what the pull request does, how, and why.
[ ] Run `mvn clean install -Psourcecheck` in your module with source check enabled to make sure basic checks pass and there are no checkstyle violations. A more thorough check will be performed on your pull request automatically.
Below are the contribution guidelines:
https://github.com/apache/camel/blob/master/CONTRIBUTING.md