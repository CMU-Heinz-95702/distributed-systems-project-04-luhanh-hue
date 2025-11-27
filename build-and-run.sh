#!/usr/bin/env bash
set -e

echo "🛠  Building ROOT.war with Maven wrapper..."
./mvnw -q clean package

echo "📦 Copying ROOT.war to repo root..."
cp target/ROOT.war ROOT.war

echo
echo "✅ Build done."
echo "- Commit & push this ROOT.war to GitHub."
echo "- If Tomcat isn’t picking it up, use 'Codespaces → Rebuild container' so Docker rebuilds with your new ROOT.war."
