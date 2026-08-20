---
navigation:
  title: "Ender Amulet"
  icon: "anvilcraft_portable_addon:ender_amulet"
items:
  - anvilcraft_portable_addon:ender_amulet
---

# Ender Amulet

<item id="anvilcraft_portable_addon:ender_amulet"/>

# Obtaining

The Ender Amulet is crafted through AnvilCraft's **Jewel Crafting**:

<recipe id="anvilcraft_portable_addon:ender_amulet"/>

# Binding a Pillar

1. Hold the Ender Amulet and **sneak-right-click** a placed <ref item="anvilcraft_portable_addon:ender_amulet_pillar"/> to bind it to that pillar
2. Once bound, a player carrying the amulet (main hand / off hand / Curios charm slot) gains the effects of all charms hung on the pillar
3. **Right-click** air to unbind
4. A bound amulet glows and its tooltip shows the bound pillar's coordinates and dimension

# Working Across Dimensions

- While carrying a bound amulet, the charms' effects keep applying to the player as long as the pillar's chunk is loaded
- The `crossDimensionChunkLoad` option (default on) **force-loads** the pillar's chunk while you hold the amulet, giving true cross-dimension portability

<tip>
Hang your frequently used charms (potions, damage immunity, etc.) all over a pillar, bind one Ender Amulet and carry it — it is like carrying a whole "charm warehouse" in your pocket
</tip>

# Other

- It hooks the AnvilCraft AmuletEvent, so effects are identical to carrying the original charms
- Force-loaded chunks are released automatically when you log out or stop holding the amulet