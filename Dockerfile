FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /opt/ledger
RUN useradd --system --uid 10001 ledger
COPY --from=build /src/target/fintech-ledger-core.jar app.jar
USER ledger
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
