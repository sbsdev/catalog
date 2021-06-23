FROM openjdk:8-alpine

COPY target/uberjar/catalog.jar /catalog/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/catalog/app.jar"]
