# Use Tomcat 10 with JDK 21 (Jakarta EE compatible)
FROM tomcat:10.1-jdk21-temurin-jammy

# Clean out default webapps (manager, docs, etc.)
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy built WAR as ROOT.war so the app is deployed at /
COPY ROOT.war /usr/local/tomcat/webapps/ROOT.war

# Expose 8080 for Codespaces
EXPOSE 8080

# Start Tomcat in the foreground
CMD ["catalina.sh", "run"]
