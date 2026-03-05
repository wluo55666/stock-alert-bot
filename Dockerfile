# Use official Java 24 image
FROM eclipse-temurin:24-jdk-alpine

# Set working directory
WORKDIR /app

# Copy build artifact
COPY build/libs/stock-alert-bot-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
