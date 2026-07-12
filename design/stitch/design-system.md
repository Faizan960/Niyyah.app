## Brand & Style
The design system is anchored in the concept of *Sukun* (tranquility). It avoids the clutter of traditional utility apps to create a digital sanctuary for spiritual intention. Drawing inspiration from modern Islamic architecture, the aesthetic balances the mathematical precision of Swiss editorial design with the organic warmth of tactile materials. 

The style is **Premium Minimalism** with a focus on **Editorial Typography**. It utilizes a layered surface approach rather than heavy ornamentation, ensuring that the content—whether a Quranic verse, a prayer time, or a personal reflection—remains the sole focus. The emotional response is one of intentionality, calm, and timelessness.

## Layout & Spacing
This design system utilizes a **Dynamic Padding** model rather than a rigid grid. 

- **Safe Margins:** A minimum of 24px horizontal padding on mobile to create a "breathable" frame around content.
- **Whitespace:** Use `spacing-2xl` or `3xl` to separate major content sections (e.g., separating the "Daily Verse" from "Prayer Times").
- **Vertical Rhythm:** Content blocks should follow a consistent vertical flow, using `spacing-md` for internal card padding and `spacing-xl` for external margins.
- **Stacking:** Elements should feel like they are placed on a physical surface; use generous margins to prevent the UI from feeling "app-like" and more "page-like."

## Elevation & Depth
Elevation is conveyed through **Tonal Layering** and **Subtle Outlines** rather than traditional drop shadows.

- **Level 0 (Background):** #F7F6F3. The base canvas.
- **Level 1 (Cards/Containers):** #FFFFFF. These sit flat on the background with a 1px border (#E7E2DA).
- **Level 2 (Active/Elevated):** #FCFBF9. Used for elements that are currently being interacted with or require subtle prominence.
- **Shadows:** When necessary for functional depth (like a bottom sheet), use a "Sunlit" shadow: `0px 4px 20px rgba(30, 45, 76, 0.04)`. It should be barely perceptible, appearing more as a soft glow.

## Components
- **Buttons:** 
  - *Primary:* Deep Navy background, white text. No shadow, pill-shaped.
  - *Secondary:* Transparent background, Deep Navy 1px border.
  - *Text:* Sage or Navy text, no background.
- **Cards:** White background, 16px-24px corner radius, 1px subtle border. Internal padding should be at least 24px.
- **Inputs:** Underlined or lightly boxed. Use "Warm Stone" for labels. Focus state shifts the border to "Primary Navy."
- **Navigation:** A minimalist bottom bar with no top border (use a soft blur or tonal shift). Icons are Outlined Material Symbols, 24px. Only the active icon receives a small dot indicator or color shift to "Primary Navy."
- **Chips/Badges:** Small, pill-shaped, using "Secondary Background" (#F2F0EC) with "Text Secondary" text.
- **Progress Bars:** Thin (4px), using "Secondary Emerald" for the track and "Sage" for the background.
- **Transitions:** All transitions are 250ms Ease Out. Use "Fade + Subtle Slide Up" for new screens to mimic the turning of a page.