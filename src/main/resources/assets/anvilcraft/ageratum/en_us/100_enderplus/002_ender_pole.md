---
navigation:
  title: "Ender Pole"
  icon: "anvilcraft_enderplus:enderpole"
items:
  - anvilcraft_enderplus:enderpole
---

# Ender Pole

<item id="anvilcraft_enderplus:enderpole"/>

# Obtaining

<recipe id="anvilcraft_enderplus:enderpole"/>

# Function

The Ender Pole is a **3-block-tall** block. It can act both as a **transmitter** of the local grid and,
once two poles are bound to each other, turn the two grids into a single "shared grid", enabling
**cross-dimension power transmission**.

# Binding & Transmission

1. With an Ender Pole in hand, **right-click** a placed pole
2. **Right-click** another pole: the two poles are **mutually bound** into one grid
3. Once bound, generation / consumption on both sides stay identical, equivalent to one grid
4. One pole can bind to **unlimited** poles; all of them form a **single grid**
5. Sneak + right-click air: clear the recorded bind target on the item
6. Sneak + right-click a bound pole with an empty hand: unbind that pole

# Redstone Control

- Feeding a **redstone signal** to the pole's **bottom segment** cuts off transmission
- Transmission resumes when the signal disappears

# Working Across Dimensions

- The `crossDimensionChunkLoad` option (default on; it also controls the talisman pillar) **force-loads** the chunks of both poles when you bind two poles together, giving true cross-dimension transmission

<warning>
Cross-dimension transmission requires both chunks to be loaded; it fails if the remote chunk is unloaded. The addon's talisman pillar force-loads its chunk, and the Ender Pole does the same
</warning>
