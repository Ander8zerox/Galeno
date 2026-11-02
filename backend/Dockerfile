# Imagen única para local, EC2 (Capa 1) y ECS Fargate (Capa 3).
# El JAR no depende de la arquitectura: se compila en la del equipo que construye (sin emulación) y solo la imagen
# final es de la plataforma pedida (linux/arm64 para la EC2 t4g).
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/galeno-api-*.jar app.jar
USER app
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
