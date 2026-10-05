---
navigation:
  title: Starlight P2P Tunnel
  parent: appliedas:index.md
  position: 29
  icon: appliedas:starlight_p2p_tunnel
item_ids:
  - appliedas:starlight_p2p_tunnel
---
# Starlight P2P Tunnel

Transfer starlight from focal points, focus crystals and lenses through ME.

<Recipe id="appliedas:starlight_p2p_tunnel" />

You can also right-click an existing P2P tunnel with a **Lens** to convert it.

## Setup

1. Install the input and outputs on cables in the same ME network. Each tunnel needs power and one channel.
2. **Sneak-right-click the input with a Memory Card**, then right-click each output with that card.
3. Place the receiving device in the block directly in front of an output.

## Input

- **Focal point:** Place the input's cable block in the focal point's center column with open sky. It supplies starlight at night.
- **Crystal or lens:** Use a **Linking Tool** to connect it to the input's cable block. Keep the beam path clear.

## Output

- Focal-point light can supply a [Starlight Transmutation Chamber](transmutation.md) or activate a **focus crystal attuned to the same constellation**. It must pass through a focus crystal before lenses can transmit it.
- Light from crystals or lenses can supply a device directly or continue through a lens.
- Outputs can also perform starlight block transmutation when the recipe's starlight requirements are met.

Multiple outputs share the starlight equally; inputs on the same cable also split their supply. Tunnels do not store starlight. Transmission stops when supply ends, the network goes offline or a chunk along the path unloads.
