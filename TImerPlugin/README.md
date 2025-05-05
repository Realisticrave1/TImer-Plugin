# Timer Plugin

A Minecraft Spigot plugin that adds a global timer with unique gameplay mechanics. Players can modify the timer after getting kills, with a lives system that adds strategic depth to your server.

## Features

- **Global Timer Display**: Shows time remaining as a boss bar for all players
- **Kill Rewards**: Players who kill others receive special items to modify the timer
- **Lives System**: Each player has a limited number of lives
- **Special Items**:
    - **Time Controller**: Freeze nearby players for a few seconds
    - **Time Mace**: Dash forward and create time explosions
    - **Revive Totem**: Bring back banned players
- **Dynamic GUI**: Centered modification interface regardless of configuration
- **Fully Configurable**: Customize all aspects of the plugin through the config

## Installation

1. Download the compiled plugin JAR file
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. The plugin will generate default configuration files

## Configuration

The main configuration file is `config.yml`, located in the `plugins/TImerPlugin` folder after first run.

### Timer Settings

```yaml
timer:
  initial-days: 30.0    # Starting time in days
  max-days: 30          # Maximum possible time
  modification:
    max-add: 2          # Maximum days a player can add at once
    max-remove: 2       # Maximum days a player can remove at once
    min-days-to-remove: 2  # Minimum timer value required to allow removal
```

### Lives System

```yaml
lives:
  initial-lives: 10     # Starting lives for new players
  revive-lives: 8       # Lives when a player is revived
```

### Special Items Settings

```yaml
special-items:
  time-controller:
    freeze-duration: 3  # Seconds
    cooldown: 30        # Seconds
    freeze-radius: 10   # Blocks

time-mace:
  max-dashes: 3
  cooldown: 120        # Seconds
  dash-strength: 2.5
  explosion:
    radius: 8          # Blocks
    duration: 5        # Seconds
    slowness-level: 3  # Effect amplifier
    enabled: true
```

### Effects Settings

```yaml
effects:
  add-time:
    sound: BLOCK_BEACON_AMBIENT
    sound-volume: 1.0
    sound-pitch: 1.0
    duration: 5         # Seconds
```

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/timerplugin info` | Show plugin information | timerplugin.use |
| `/timerplugin settime <days>` | Set the timer value | timerplugin.admin |
| `/timerplugin reload` | Reload the configuration | timerplugin.admin |
| `/timerplugin setlives <player> <lives>` | Set a player's lives | timerplugin.admin |
| `/timerplugin givespecial <player> <item>` | Give special items | timerplugin.admin |
| `/lives [player]` | Check your or another player's lives | timerplugin.use |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `timerplugin.use` | Allow use of basic plugin commands | All players |
| `timerplugin.admin` | Allow use of admin commands | Operators |

## Custom Items

### Time Modifier
- Obtained by killing another player
- Right-click to open a GUI to add or remove days from the timer
- Adding time gives you an extra life

### Time Controller
- Freezes all players around you for a configurable duration
- Uses custom Heart of the Sea in crafting

### Time Mace
- Allows you to dash forward
- Shift + right-click creates a time explosion
- Uses custom Heart of the Sea in crafting

### Revive Totem
- Allows you to revive banned players
- Uses custom Heart of the Sea in crafting

## Troubleshooting

If you encounter any issues:

1. Check the server console for error messages
2. Make sure you're using the correct Minecraft version (1.21.1+)
3. Verify that the config.yml file is properly formatted
4. Try using `/timerplugin reload` to reload the configuration

For bug reports, contact the developer.

## License

This plugin is provided as-is with no warranty. You are free to use and modify it for your own server.