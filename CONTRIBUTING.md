## Contributing to Camel

There are many ways you can help make Camel a better piece of software - please dive in and help!
- Try surf the documentation - if somethings confusing or not clear, let us know. 
- Download the code & try it out and see what you think. 
- Browse the source code. Got an itch to scratch, want to tune some operation or add some feature?
- Want to do some hacking on Camel? Try surfing the our [issue tracker](http://issues.apache.org/activemq/browse/CAMEL) for open issues or features that need to be implemented, take ownership of an issue and try fix it.
- If you are a new Camel rider and would like to help us, you can also find [some easy to resolve issues.](https://issues.apache.org/jira/secure/IssueNavigator.jspa?mode=hide&requestId=12316782) 
- Leave a comment on the issue to let us know you are working on it and add yourself as a watcher to get informed about all modifications.

## Table of Contents

- [Getting in touch](#getting-in-touch)
— [Improving the documentation](#improving-the-documentation)
- [If you find a bug or problem](#if-you-find-a-bug-or-problem)
- [Working on the code](#working-on-the-code)

## Getting in touch
There are various ways of communicating with the Camel community.
- Join us on the [Discussion Forums](http://camel.apache.org/discussion-forums.html) and take part in any conversations
- Pop by on [IRC](http://camel.apache.org/irc-room.html) and say hi
- Add some comments to the [wiki](http://camel.apache.org/navigation.html)

## Improving the documentation
Documentation is massively important to help users make the most of Apache Camel and its probably the area that needs the most help!
So if you are interested in helping the documentation effort; whether its just to fix a page here or there, correct a link or even write a tutorial or improve what documentation is already there please do dive in and help!
Most of the documentation is stored on the wiki. We are currently moving the documentation into the code (AsciiDoc). From there it is automatically converted to the wiki. So before editing the wiki check the code because otherwise your changes may be lost. This transition is work-in-progress.

See [How does the website work](http://camel.apache.org/how-does-the-website-work.html) or [How do I edit the website for more details](http://camel.apache.org/how-do-i-edit-the-website.html).
To be able to edit the wiki you need
- an appropriate licence agreement on file with the ASF
- an account on the wiki (on the bottom of each page there is an edit button, that allows you to create an account)
- karma - mail the dev list asking for permission (to prevent spam we only offer access to the wiki by folks sending mail to the mailing list).

## If you find a bug or problem
Please raise a new issue in our [issue tracker](http://issues.apache.org/activemq/browse/CAMEL)
If you can create a JUnit test case then your issue is more likely to be resolved quicker.
e.g. take a look at some of the existing [unit tests cases](https://svn.apache.org/repos/asf/camel/trunk/camel-core/src/test/java/)
Then we can add your issue to Subversion and then we'll know when its really fixed and we can ensure that the problem stays fixed in future releases.

## Working on the code
We recommend to work on the code from [github](https://github.com/apache/camel/).

        git clone https://github.com/apache/camel.git
        cd camel

Build the project (without testing).

        mvn clean install -Dtest=**false**