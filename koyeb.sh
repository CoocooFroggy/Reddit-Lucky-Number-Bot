#!/bin/bash

# Start the netcat server to serve the current date on port 8080
while true; do
    echo -e "HTTP/1.1 200 OK\r\n\r\n$(date)" | nc -l -p 8080;
done &

# Run the Java application
java -jar "Reddit Lucky Number Bot-1.0-all.jar"
