# ItemStacks
Only one version has been tested: paper 1.21.11; in the future, we will check all the files and fix certain errors.

A powerful and lightweight Minecraft plugin designed to give server administrators complete control over item stack sizes. Break the vanilla limits and customize the maximum stack size for any item in the game, from 1 to 99.

## Overview

**ItemStacks** allows you to modify the default stack size of items dynamically using the modern `DataComponentTypes` API. Whether you want to make blocks stack to 64, or limit certain items to a maximum of 16, this plugin handles it efficiently without causing server lag.

Built with cross-platform compatibility in mind, it supports **Paper**, **Bukkit**, and **Folia**.

## Features

- **Custom Stack Sizes:** Change the max stack size of any item between 1 and 99.
- **Dynamic Updates:** Items are automatically updated when players join, open inventories, pick up items, or click.
- **Multi-Language Support:** Comes with built-in English and Russian localization. Easily add your own languages.
- **Modern Command API:** Utilizes the Brigadier command system for advanced tab completion and argument validation.
- **Folia Compatible:** Fully supports Folia's regionized multithreading architecture.
- **Lightweight:** No unnecessary bloat, ensuring optimal server performance.

## Installation

1. Download the latest `.jar` file from the releases section (or build it yourself).
2. Drop the `.jar` file into your server's `plugins` folder.
3. Restart your server.
4. Configure your desired stack sizes in the `ItemStacks.yml` file.
5. Run `/itemstack reload` to apply changes without restarting.

## Commands & Permissions

All commands require Operator (OP) status by default.

| Command | Description |
| :--- | :--- |
| `/itemstack help` | Displays the help menu and available commands. |
| `/itemstack reload` | Reloads the plugin configuration and language files. |
| `/itemstack item additem <item> <count>` | Sets the max stack size for a specific item. |
| `/itemstack item delitem <item>` | Removes the custom stack size for an item (resets to vanilla). |
| `/itemstack item status <item>` | Checks the current custom stack size of an item. |
| `/itemstack language <lang>` | Switches the plugin language (e.g., `en`, `ru`). |

*Alias: `/is`*

### Examples

```text
/itemstack item additem minecraft.tnt 99
/itemstack item additem diamond 32
/itemstack item delitem minecraft.tnt

### Files

Edit the files in plugins\ItemStacks\ and run /itemstacks reload to apply the changes.

