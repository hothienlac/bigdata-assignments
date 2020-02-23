FROM maven:3.5.4-jdk-8-alpine

ENV APP_NAME=assignment1-application.jar

ENV VCAP_SERVICES='{}'
ENV destinations='[{name: "ErpQueryEndpoint", url: "${marketing_cloud_url}", username: "${marketing_cloud_username}", password: "${marketing_cloud_password}", properties: [{key: "TrustAll", value: "false"}]}]'

RUN apk add --update jq && rm -rf /var/cache/apk/*

RUN mkdir /app
COPY ./application/target/$APP_NAME /app

WORKDIR /app
CMD java -Xmx512m -jar $APP_NAME