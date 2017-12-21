# Small linux distro with the Java Development Kit for compiling/running Java
FROM openjdk:alpine

# Do things in container at path /app
WORKDIR /app

# Copy files from this directory into /app in the container
ADD . /app

# Port 80 should be accessible outside of the container
EXPOSE 80

CMD ["sh", "main.sh"]
