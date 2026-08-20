---
navigation:
  title: "Ender Amulet"
  icon: "anvilcraft_enderplus:ender_amulet"
items:
  - anvilcraft_enderplus:ender_amulet
---

# Ender Amulet

<item id="anvilcraft_enderplus:ender_amulet"/>

# Obtaining

Obtain it by taking **fatal enderman damage** while holding a charm box that contains a **Totem of Undying**.
The Ender Amulet can also be duplicated at the Jewel Crafting table.
When the box's totem is consumed, the initial raffle chance is raised to **20%**, each failed raffle raises it
by **10%**, the chance caps at **100%**, and it stays at **0%** while you already carry such an amulet in your inventory.

<recipe id="anvilcraft_enderplus:ender_amulet"/>

# Binding a Pillar

1. Hold the Ender Amulet and **sneak-right-click** a placed <ref item="anvilcraft_enderplus:ender_amulet_pillar"/> to bind it to that pillar
2. Once bound, a player carrying the amulet (main hand / off hand / Curios charm slot) gains the effects of all charms hung on the pillar
3. **Right-click** air to unbind
4. A bound amulet glows and its tooltip shows the bound pillar's coordinates and dimension

# Working Across Dimensions

- While carrying a bound amulet, the charms' effects keep applying to the player as long as the pillar's chunk is loaded
- The `crossDimensionChunkLoad` option (default on) **force-loads** the pillar's chunk while you hold the amulet, giving true cross-dimension portability

# Other

- Force-loaded chunks are released automatically when you log out or stop holding the amulet
