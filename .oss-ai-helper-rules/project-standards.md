# Project Standards

This rule file contains build tools, commands, and code style constraints for the project. Commands read this file to determine how to build, test, and format code.

- **Build tool:** Maven
- **Build command:** `mvn verify`
- **Test command:** `mvn verify`
- **Format command:** `cd <module> && mvn -DskipTests install`
- **Module-specific build:** yes (always run `mvn` in the module directory where changes occurred)
- **Parallelized Maven:** no (resource intensive, do NOT parallelize Maven jobs)
- **Code style restrictions:**
  - Do NOT use Lombok (unless already present in the file)
  - Do NOT change public API signatures without justification
  - Do NOT add new dependencies without justification
  - Records are allowed for internal/non-API classes; do NOT convert existing public API classes to Records
  - Maintain backwards compatibility for public APIs

