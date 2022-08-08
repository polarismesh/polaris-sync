FROM java:8

ARG version

COPY polaris-sync-server/target/polaris-sync-server-${version}.jar /app/polaris-sync-server.jar

WORKDIR /app

RUN chmod 777 /app/

CMD ["java", "-jar", "polaris-sync-server.jar"]