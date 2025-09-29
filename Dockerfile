# build
FROM gradle:8.8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle clean bootJar -x test

# run
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -m appuser
COPY build/libs/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
USER appuser
EXPOSE 8080
CMD ["sh","-c","java $JAVA_OPTS -jar app.jar"]
