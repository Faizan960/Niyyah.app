## Brand & Style
The design system for the dark mode experience is rooted in a philosophy of **Serene Premiumism**. It is designed to evoke a sense of quietude, intentionality, and spiritual focus. By utilizing a deep, atmospheric palette, the interface recedes to allow content—specifically sacred or reflective text—to become the focal point.

The style is **Editorial Minimalism**, drawing inspiration from high-end luxury journals and modern museum curation. It avoids the harshness of pure black, opting instead for a sophisticated charcoal-teal foundation that feels "alive" even in low light. The aesthetic is characterized by expansive whitespace (breathing room), thin hairlines, and a rejection of traditional skeuomorphism in favor of tonal depth and tactile motion.

## Layout & Spacing
This design system utilizes a **Fixed-Fluid Hybrid** grid. 
- **Desktop**: 12-column grid with 24px gutters and 80px margins.
- **Mobile**: 4-column grid with 16px gutters and 20px margins.

The spacing rhythm is strictly mathematical, built on an 8pt base but customized to include 12px and 20px steps for finer editorial control. Layouts should prioritize center-alignment for hero content and wide internal padding (minimum 24px) within cards to maintain the feeling of luxury and space.

## Elevation & Depth
In this dark theme, depth is communicated through **Luminance Layering** rather than shadows. 
- **Level 0 (Base)**: `#0B0F10` — The canvas.
- **Level 1 (Cards/Lists)**: `#151B1C` — Subtle separation from base.
- **Level 2 (Modals/Overlays)**: `#232E31` — Highest prominence.

**Glassmorphism** is applied to persistent navigation bars: Use a background color of `#0B0F10` at 70% opacity with a 20px backdrop blur and a 0.5px border of `#2A3335` to define the edge. This creates a "dark lens" effect that feels integrated with the content passing beneath it.

## Components

### Buttons
- **Primary**: Background `#18A67A`, Text `#F5F5F5`. 4px radius. 150ms hover transition to a slightly brighter emerald.
- **Secondary**: Border 1px `#2A3335`, Background transparent, Text `#F5F5F5`.
- **Tertiary/Ghost**: Text `#A7A7A7`, no border.

### Input Fields
- Background: `#12181A`. Border: 1px `#2A3335`. 
- Focus State: Border changes to `#18A67A` (Emerald) with no outer glow.
- Placeholder Text: `#808080`.

### Chips
- Compact height (32px), 4px radius, Background `#232E31`, Text `#A7A7A7`. Active state uses Navy `#253B63` background with Emerald text.

### Cards
- Background: `#151B1C`. Border: 1px `#2A3335`. No shadow. Internal padding should always be `lg` (24px) or higher.

### Motion & Transitions
- **Interaction**: On press, all interactive elements scale to 98% (`scale(0.98)`) using a 120ms "Ease Out" transition.
- **Transitions**: Screen entries use a 300ms "Decelerate" curve with a subtle vertical slide-up of 12px and a simultaneous fade-in.
- **Feedback**: For loading, use a subtle linear shimmer skeleton (gradient from `#151B1C` to `#232E31`). Avoid circular spinners to maintain the peaceful atmosphere.