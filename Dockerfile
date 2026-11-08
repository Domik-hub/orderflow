FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S orderflow && adduser -S orderflow -G orderflow
WORKDIR /app
COPY --from=build /app/target/orderflow-1.0.0.jar app.jar
USER orderflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
