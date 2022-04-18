# getrun 
is a java cli tool for downloading an artifact plus runtime dependencies from maven repo and running it all from the command line

# Quick demo
The following will download the rotala web framework boilerplate and run an example server.
```bash
git clone git@github.com:blyxa/getrun.git
cd getrun
./gradlew build
java -jar build/libs/getrun-1.0.0.jar io.github.blyxa rotala 0.0.1-SNAPSHOT io.github.blyxa.rotala.ExampleMain
```

