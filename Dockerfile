# Use a Linix image with Tomcat 10
FROM tomcat:10.1.0-M5-jdk16-openjdk-slim-bullseye

ENV ATLAS_URI="mongodb+srv://luhanhuang_db_user:nX7EJuq1bufZiMPC@cluster0.004kzja.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0"
# Copy in our ROOT.war to the right place in the container
COPY ROOT.war /usr/local/tomcat/webapps/

