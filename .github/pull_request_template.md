# Description

<!--
- Write a pull request description that is detailed enough to understand what the pull request does, how, and why.
-->

# Target

- [ ] I checked that the commit is targeting the correct branch (note that Camel 3 uses `camel-3.x`, whereas Camel 4 uses the `main` branch)

# Tracking
- [ ] If this is a large change, bug fix, or code improvement, I checked there is a [JIRA issue](https://issues.apache.org/jira/browse/CAMEL) filed for the change (usually before you start working on it).

<!--
# *Note*: trivial changes like, typos, minor documentation fixes and other small items do not require a JIRA issue. In this case your pull request should address just this issue, without pulling in other changes.
-->

# Apache Camel coding standards and style

- [ ] I checked that each commit in the pull request has a meaningful subject line and body.

<!--
If you're unsure, you can format the pull request title like `[CAMEL-XXX] Fixes bug in camel-file component`, where you replace `CAMEL-XXX` with the appropriate JIRA issue.
-->

- [ ] I have run `mvn clean install -DskipTests` locally and I have committed all auto-generated changes

<!--
You can run the aforementioned command in your module so that the build auto-formats your code. This will also be verified as part of the checks and your PR may be rejected if if there are uncommited changes after running `mvn clean install -DskipTests`.

You can learn more about the contribution guidelines at https://github.com/apache/camel/blob/main/CONTRIBUTING.md
-->

