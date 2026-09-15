# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

ARG MAVEN_SKIP_TESTS=true

COPY timvalidator/pom.xml timvalidator/pom.xml
RUN mvn -f timvalidator/pom.xml -DskipTests=${MAVEN_SKIP_TESTS} dependency:go-offline

COPY timvalidator/src timvalidator/src
RUN mvn -f timvalidator/pom.xml -DskipTests=${MAVEN_SKIP_TESTS} install

COPY timvalidator-api/pom.xml timvalidator-api/pom.xml
RUN mvn -f timvalidator-api/pom.xml -DskipTests=${MAVEN_SKIP_TESTS} dependency:go-offline

COPY timvalidator-api/src timvalidator-api/src
RUN mvn -f timvalidator-api/pom.xml -DskipTests=${MAVEN_SKIP_TESTS} package

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/timvalidator-api/target/timvalidator-api-*.jar /app/app.jar
COPY --from=build /workspace/timvalidator-api/target/libs/libasnapplication.so /app/libs/libasnapplication.so

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]