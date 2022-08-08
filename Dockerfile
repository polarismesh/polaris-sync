FROM java:8

COPY polaris-sync-server/target/polaris-sync-${version}.jar /app/polaris-sync-server.jar

WORKDIR /app

RUN chmod 777 /app/

CMD ["java", "-jar", "polaris-sync-server.jar"]