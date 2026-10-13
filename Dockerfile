FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/renttool-bootstrap/target/renttool-bootstrap-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir -p /app/uploads && chown 10001:10001 /app/uploads
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
