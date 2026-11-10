# Elytra Swap (Fabric, Minecraft 26.3)

Press one key to toggle your chest slot between an elytra and a chestplate.

- Wearing a chestplate (or anything else, or nothing): it puts on your best elytra (most durability left, never a broken one). The old item goes where the elytra was.
- Wearing an elytra: it puts your chestplate back on. It picks the one it took off last time if it's still in the same slot, otherwise the first chestplate it finds.
- Item in your hotbar or offhand: one packet. Item in your main inventory: three clicks.
- Works with modded gliders too (anything with the vanilla `glider` component).
- Default key: **`** (grave accent). Change it under Options > Controls > Key Binds > "Elytra Swap".
- Client-side only. Requires **Fabric API**.

## Build the .jar

You need **JDK 25** (for example Microsoft Build of OpenJDK 25 or Temurin 25).

Windows:
```
gradlew.bat build
```
macOS / Linux:
```
./gradlew build
```

The first build downloads Gradle, Minecraft and Fabric (a few minutes). The mod ends up in `build/libs/elytra-swap-1.0.0+26.3.jar`. Put it in your `.minecraft/mods` folder along with Fabric API for 26.3.

### No JDK? Build on GitHub

Push this folder to a new GitHub repository. The included workflow (`.github/workflows/build.yml`) builds it automatically. Open the **Actions** tab, then the latest run, and download **Artifacts**.

## Server note

Some servers ban inventory-swap mods. Check the rules before using it on a public server.
