Welcome to Jellystone V2 (COMP2000 group)

Members:
- Chris
- Abbas
- Lolo
- Samiha
- Atharv
- Gabriel
- Matt

Collusion is encouraged!

## Running the simulation

Use a JDK to compile the current source, then run `Main`:

```sh
javac -d out src/*.java
java -cp out Main
```

Run these commands from the project folder. You can also run `Main` from your
IDE. Keep the `images` folder alongside `src` and `out`; images are located
relative to the compiled classes, so changing the working directory does not
break image loading. Nested IDE output folders inside the project also work.

If your IDE compiles outside the project, set its working directory to the
project folder or include the project folder on the runtime classpath.
For a packaged JAR, include `images/tree.png`, `images/fire.png`, and
`images/lightning.png` inside the JAR. Missing or unreadable images produce
an error explaining the problem instead of an empty display.
