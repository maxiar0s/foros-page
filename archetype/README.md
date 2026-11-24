# Forum backend Maven archetype

This directory is a standalone Maven archetype project. It generates a minimal Java 17 Spring Boot backend skeleton (not the existing forum or users services). The archetype template is under `src/main/resources/archetype-resources`.

## Generate a backend project

From the repository root, install the archetype into the local Maven repository:

```sh
mvn -f archetype/pom.xml install
```

Then generate a project interactively:

```sh
mvn archetype:generate \
  -DarchetypeGroupId=cl.duoc \
  -DarchetypeArtifactId=forum-backend-archetype \
  -DarchetypeVersion=1.0.0 \
  -DgroupId=cl.example \
  -DartifactId=my-forum-backend \
  -Dversion=1.0.0-SNAPSHOT \
  -Dpackage=cl.example.forum \
  -DinteractiveMode=false
```

The generated directory contains a Spring Boot application, a Java 17 Maven build, and a default `application.yml`. From that directory run `mvn test` and `mvn spring-boot:run`. Generation has not been applied to the existing services; they retain their existing source and contracts.
