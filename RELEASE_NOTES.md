Experimental client-only Fabric mod for Minecraft Java 26.3 on Windows x64.

- Absolute pen coordinates from Wacom WinTab; intended for One by Wacom CTL-472-S.
- Four-corner projective calibration: F6 records top left, top right, bottom right, bottom left.
- F8 toggles drawing, F9 shows diagnostics, F10 flips Y. Lift the tip before drawing.
- Uses ordinary Minecraft look and use/attack actions with an existing ArtMap server.
- Input releases on focus loss, menus, invalid/proximity packets and a 500 ms packet timeout.
- Calibration is invalidated on world changes and excessive eye movement.
- Includes JNA; install Fabric Loader >=0.20.1, Fabric API for 26.3 and the official Wacom driver separately.

The release workflow requires a complete Gradle build, geometry/input tests, Minecraft
26.3 mixin target checks and JAR metadata/nested dependency verification on Windows
and Ubuntu before attaching the JAR. SHA256SUMS accompanies the artifact.

**Hardware/server acceptance remains unverified:** automated checks do not prove
live drawing on a CTL-472-S with a particular ArtMap version. No server modifications
are included or required by this mod. This draft prerelease is for manual testing;
see TESTING.md for the acceptance procedure. Calibration does not persist between sessions.
