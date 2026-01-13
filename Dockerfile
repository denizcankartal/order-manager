FROM maven:3-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/order-manager-*.jar /app/order-manager.jar
RUN mkdir -p /home/app

ENV HOME=/home/app

ENTRYPOINT ["java", "-Duser.home=/home/app", "-jar", "/app/order-manager.jar"]
CMD ["--help"]
