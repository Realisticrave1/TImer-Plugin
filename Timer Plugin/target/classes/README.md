# Timer Plugin

A Minecraft plugin that adds a server-wide timer that counts down from a specified number of days. Players can modify the timer after killing other players by adding or removing days. The plugin also includes a lives system and special items with unique abilities.

## Features

### Core Features
- Server-wide countdown timer displayed as a boss bar
- Players receive a special item (Heart of the Sea) after killing another player
- Interactive GUI for adding or removing days from the timer
- Customizable limits for max/min days and how many days can be added/removed
- Admin commands to manage the timer

### Lives System
- Each player starts with 10 lives
- Players lose a life when killed by another player
- Players can gain a life by adding days to the timer (after killing someone)
- When a player loses all lives, they are banned from the server
- Revive Totem can be crafted to bring back banned players (with 8 lives)

### Special Items
1. **Time Controller** - Freezes nearby players for 3 seconds
2. **Time Mace** - Allows dashing forward 3 times (with cooldown)
3. **Revive Totem** - Unbans a player who has lost all lives

## Installation

1. Download the plugin JAR file
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. The plugin will generate default configuration files at `plugins/TimerPlugin/`
5. You can edit these files to customize the plugin to your liking

## Crafting Recipes

### Time Controller
![Time Controller Recipe](time_controller_recipe.png)
- Diamond in the corners
- Heart Of The Sea in the middle edges
- Dragon Egg in the center

### Time Mace
![Time Mace Recipe](time_mace_recipe.png)
- Heart Of The Sea in the middle of each edge
- Amethyst Shard in the center

### Revive Totem
![Revive Totem Recipe](revive_totem_recipe.png)
- Diamond Blocks in the corners
- Heart Of The Sea in the top middle and bottom middle
- Netherite Ingots in the middle left and middle right
- Nether Star in the center

## Configuration

The `config.yml` file contains all the settings for the plugin:

### Timer Settings
- `timer.initial-days`: The initial value of the timer in days (default: 30.0)
- `timer.max-days`: The maximum number of days allowed for the timer (default: 30)
- `timer.modification.max-add`: Maximum number of days a player can add at once (default: 2)
- `timer.modification.max-remove`: Maximum number of days a player can remove at once (default: 2)
- `timer.modification.min-days-to-remove`: Minimum timer value required to allow day removal (default: 2)

### Lives System Settings
- `lives.initial-lives`: Starting lives for new players (default: 10)
- `lives.revive-lives`: Lives when revived (default: 8)

### Special Items Settings
Settings for cooldowns, durations, and other parameters for special items.

### Display Settings
Customize the appearance of the boss bar timer.

### Messages
Various messages displayed to players can be customized in the `messages` section.

## Commands

- `/timerplugin info` - Shows current timer information
- `/timerplugin lives [player]` - Check your lives or another player's lives
- `/timerplugin settime <days>` - Sets the timer to a specific value (admin)
- `/timerplugin reload` - Reloads the configuration (admin)
- `/timerplugin setlives <player> <lives>` - Set a player's lives (admin)
- `/timerplugin givespecial <player> <controller|mace|revive>` - Give special items (admin)

## Permissions

- `timerplugin.use` - Allows use of basic plugin commands (default: everyone)
- `timerplugin.admin` - Allows use of admin commands (default: operators)

## How It Works

### Timer System
1. The timer starts at the configured initial value and counts down in real-time
2. When a player kills another player, they receive a "Heart of the Sea" item
3. Right-clicking with the item opens a GUI where the player can choose to add or remove days
4. The item is consumed after use
5. The server is notified of the time change

### Lives System
1. Each player starts with 10 lives
2. Players lose a life when killed by another player
3. The killer can gain a life by choosing to add days to the timer
4. When a player loses all lives, they are automatically banned
5. The Revive Totem can be used to unban a player (they return with 8 lives)

### Special Items
- **Time Controller**: Freezes all entities within a 10-block radius for 3 seconds
- **Time Mace**: Allows the player to dash forward up to 3 times before going on cooldown
- **Revive Totem**: Opens a GUI to select a banned player to revive

## What Happens When Timer Reaches Zero?

Currently, the plugin broadcasts a message when the timer reaches zero. You can modify the plugin code to add custom actions when this happens, such as:

- Resetting the server
- Changing game modes
- Triggering special events

## Troubleshooting

If you encounter issues:

1. Check the server console for error messages
2. Verify the plugin is properly loaded using `/plugins` command
3. Confirm the config.yml file has valid YAML formatting
4. Make sure your server version is compatible (requires 1.16+)

## Data Storage

The plugin stores player lives and banned players in `lives.yml` file. This ensures data persistence between server restarts.