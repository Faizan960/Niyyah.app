## Brand & Style

This design system is built upon the concept of "Digital Sanctuary." It prioritizes spiritual calm and mental clarity by merging the disciplined structure of Swiss Editorial design with the organic warmth of luxury journals. The aesthetic is inspired by the tactile quality of high-end paper and the minimalist functionalism of Muji.

The emotional response should be one of immediate tranquility—as if the user is opening a beautifully bound book. The interface uses generous whitespace not as empty space, but as "breathing room" for reflection. The style is **Minimalist-Premium**, avoiding heavy shadows or unnecessary decoration in favor of exquisite typography and a sophisticated, earth-toned palette.

## Layout & Spacing

The layout follows a **Fixed-Fluid hybrid** model. On mobile, a standard 4-column grid with 24px side margins is used. For larger screens, content is constrained to a central "reading lane" to maintain line-length legibility, typical of editorial journals.

The spacing rhythm is governed by a strict **8pt grid**. Vertical rhythm is the priority; spacing between sections should be generous (40px+) to distinguish different "thoughts" or "modules." Horizontal alignment should follow the "Swiss" method—aligning text and elements to a strong vertical axis to create an invisible sense of order.

## Elevation & Depth

In alignment with the "Paper" metaphor, this design system avoids heavy drop shadows. Depth is communicated through **Tonal Layering** and **Subtle Outlines**.

- **Level 0 (Background):** #F7F6F3. The canvas.
- **Level 1 (Cards):** White (#FFFFFF) with a 1px solid border (#E7E2DA). No shadow.
- **Level 2 (Active/Elevated):** #FCFBF9 with a very soft, diffused 10% opacity navy shadow (Blur: 20px, Y: 4px).
- **Navigation:** The bottom navigation uses a **Glassmorphism Capsule**. It features a 20px backdrop blur, a semi-transparent white fill (80% opacity), and a thin white inner-border to create a "floating" effect over the content.

## Components

### Buttons
- **Primary:** Deep Navy (#1E2D4C) background with White text. 16px radius. No shadow.
- **Secondary:** Warm Stone (#CEC0BB) or Sage (#ACBDAA) at 15% opacity with primary navy text.
- **Tertiary/Ghost:** Text only in Navy, with a 1px bottom border appearing only on hover/active states.

### Chips
- Used for categories or tags. 12px radius. Secondary background (#F2F0EC) with Text Secondary (#666666).

### Input Fields
- Subtle #FFFFFF background with an #E7E2DA border. On focus, the border transitions to Primary Navy. Labels are always Label-MD (Uppercase) positioned above the field.

### Cards
- **Editorial Card:** Large serif title, generous internal padding (24px), white surface, 1px border.
- **Action Card:** Smaller, used for daily tasks (Prayers, Dhikr). Uses a checkbox on the right.

### Navigation
- A floating capsule at the bottom. Icons are **Material Symbols (Rounded)**. When a tab is active, the icon fills and a small Gold (#D4A84F) dot appears underneath.

### Bottom Sheets
- 28px top-corner radius. Uses a 4px thick "handle" at the top in #E7E2DA. Background is Elevated (#FCFBF9).