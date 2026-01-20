FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy-arm64

WORKDIR /app

# Create a minimal pom.xml for Playwright installation
RUN echo '<?xml version="1.0" encoding="UTF-8"?>\n\
<project>\n\
    <modelVersion>4.0.0</modelVersion>\n\
    <groupId>com.playwright</groupId>\n\
    <artifactId>browser-installer</artifactId>\n\
    <version>1.0</version>\n\
    <dependencies>\n\
        <dependency>\n\
            <groupId>com.microsoft.playwright</groupId>\n\
            <artifactId>playwright</artifactId>\n\
            <version>1.44.0</version>\n\
        </dependency>\n\
    </dependencies>\n\
</project>' > pom.xml

# Install Chromium browser during build
RUN mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"

# Set flag to prevent additional downloads at runtime
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]