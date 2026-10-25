# Сборка: Maven внутри контейнера, тесты гоняет CI (им нужен Docker для Testcontainers)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Запуск: только JRE и jar, процесс не от root
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S bot && adduser -S bot -G bot
USER bot
COPY --from=build /app/target/brief-bot.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
