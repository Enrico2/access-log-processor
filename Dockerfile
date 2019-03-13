FROM openjdk:8-jre-alpine
RUN mkdir -p /opt/app
WORKDIR /opt/app
COPY ./datadog-assembly-0.1.jar ./
ENTRYPOINT ["java", "-jar", "datadog-assembly-0.1.jar"]
CMD ["-l", "/tmp/access.log"]