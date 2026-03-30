## ---------------------------------------------------------------------------
## Multi-stage build: compiles SpringPluggableMcp lib, then McpHostApp
## ---------------------------------------------------------------------------

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy library project and install to local repo
COPY SpringPluggableMcp/pom.xml SpringPluggableMcp/pom.xml
COPY SpringPluggableMcp/src     SpringPluggableMcp/src
RUN mvn -f SpringPluggableMcp/pom.xml install -DskipTests -q

# Copy host app project and package
COPY McpHostApp/pom.xml McpHostApp/pom.xml
COPY McpHostApp/src     McpHostApp/src
RUN mvn -f McpHostApp/pom.xml package -DskipTests -q

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/McpHostApp/target/mcp-host-app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
