FROM openjdk:alpine
WORKDIR /opt/docker
ADD target/docker/stage/opt /opt
RUN adduser -D -u 1000 drt-admin

RUN ["chown", "-R", "1000:1000", "."]

RUN apk --update add bash less curl
RUN rm -rf /var/cache/apk/*

RUN mkdir -p /var/data
RUN chown 1000:1000 -R /var/data

USER 1000

ENTRYPOINT ["bin/drt-cirium"]
