
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src

RUN mvn -B -s /root/.m2/settings.xml -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-10000} --server.address=0.0.0.0"]
