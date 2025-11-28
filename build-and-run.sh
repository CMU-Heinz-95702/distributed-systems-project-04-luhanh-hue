#!/bin/bash

# Script to build and run the DS Project 04 application
# This script assumes all required files are in the same directory as this script

set -e  # Exit on any error

# Change to the directory where this script is located
cd "$(dirname "$0")"

echo "Starting build and deployment process..."

# Step 1: Maven clean and package
# echo "Step 1: Running Maven clean package..."
# mvn clean package

# Step 2: Docker build
echo "Step 2: Building Docker image..."
docker build -t ds-project-04 .

echo "Step 2.1: Cleaning up any existing container..."
docker rm -f ds-project-04-container 2>/dev/null || true

# Step 3: Docker run
echo "Step 3: Running Docker container..."
docker run -d -p 8080:8080 --name ds-project-04-container ds-project-04

echo "Build and deployment completed successfully!"
echo "Container 'ds-project-04-container' is running on port 8080"