---
navigation:
  title: "Ender Pole"
  icon: "anvilcraft_portable_addon:enderpole"
  position: 1
items:
  - anvilcraft_portable_addon:enderpole
---

# Ender Pole

<item id="anvilcraft_portable_addon:enderpole"/>

# Obtaining

<recipe id="anvilcraft_portable_addon:enderpole"/>

# Function

The Ender Pole is a **3-block-tall** multi-part block. It acts both as a **transmitter** of the local grid and,
once two poles are bound to each other, turns the two grids into a single "shared grid", enabling
**cross-dimension power transmission**.

# Binding & Transmission

1. With an Ender Pole in hand, **right-click** a placed pole: if no target is recorded, this pole is recorded as the bind target
2. **Right-click** another pole: the two poles are **mutually bound** into a transmission link
3. Once bound, generation / consumption on both sides stay identical, equivalent to one grid; an overload on either side overloads both
4. Sneak + right-click air: clear the recorded bind target on the item
5. Sneak + right-click a bound pole with an empty hand: unbind that pole

# Redstone Control

- Feeding a **redstone signal** to the pole's **bottom segment** cuts off transmission
- Transmission resumes when the signal disappears

<warning>
Cross-dimension transmission requires both chunks to be loaded; it fails if the remote chunk is unloaded. The addon's talisman pillar force-loads its chunk, but the Ender Pole does not
</warning>

# Other

- When powered, the top segment is fully lit; on overload it dims
- Can be removed with the AnvilCraft hammer