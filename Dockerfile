# Stage 1: Build the application
FROM maven:3.8.7-eclipse-temurin-17 AS build

# Set the working directory in the container
WORKDIR /app

# Copy the pom.xml and download dependencies only (caching dependencies if unchanged)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the rest of the project files
COPY src src

# Package the application as a JAR file
RUN mvn package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jdk

# Set the working directory
WORKDIR /app

# Copy only the built JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

ENV PORT 8080

# Run the application with JVM memory settings
# Set heap size to 2GB (adjust based on your container resources)
# -Xmx2g: Maximum heap size
# -Xms1g: Initial heap size  
# -XX:+UseG1GC: Use G1 garbage collector for better memory management
# -XX:MaxGCPauseMillis=200: Target max GC pause time
# Spring Boot will automatically use the PORT environment variable
ENTRYPOINT ["java", "-Xmx2g", "-Xms1g", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", "-jar", "app.jar"]
