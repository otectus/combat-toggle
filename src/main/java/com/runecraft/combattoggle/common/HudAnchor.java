package com.runecraft.combattoggle.common;

/**
 * Where on the screen the HUD indicator anchors. Pure POJO — referenced by both the dedicated-server-loadable
 * config spec and the client-only HUD overlay, so it MUST NOT import any net.minecraft.client.* class.
 *
 * <p>The first nine values position the HUD relative to a corner / edge / center; offsets in the
 * config are then applied as inward pixel offsets from that anchor. {@link #CUSTOM} treats the
 * configured offsets as absolute screen coordinates instead.
 */
public enum HudAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    CUSTOM;

    /**
     * Resolves the top-left pixel coordinate of the HUD given the configured anchor + offsets and the
     * current screen / HUD dimensions. Centered anchors use integer division; the offsets are *inward*
     * from the anchor edge, so a {@code BOTTOM_RIGHT} with {@code (offX=10, offY=10)} keeps the HUD
     * 10 px from both the right and bottom edges. {@link #CUSTOM} treats the offsets as absolute.
     */
    public static int[] computeXY(HudAnchor a, int offX, int offY, int screenW, int screenH, int hudW, int hudH) {
        int x;
        int y;
        switch (a) {
            case TOP_LEFT:      x = offX;                            y = offY;                            break;
            case TOP_CENTER:    x = (screenW - hudW) / 2 + offX;     y = offY;                            break;
            case TOP_RIGHT:     x = screenW - hudW - offX;           y = offY;                            break;
            case CENTER_LEFT:   x = offX;                            y = (screenH - hudH) / 2 + offY;     break;
            case CENTER:        x = (screenW - hudW) / 2 + offX;     y = (screenH - hudH) / 2 + offY;     break;
            case CENTER_RIGHT:  x = screenW - hudW - offX;           y = (screenH - hudH) / 2 + offY;     break;
            case BOTTOM_LEFT:   x = offX;                            y = screenH - hudH - offY;           break;
            case BOTTOM_CENTER: x = (screenW - hudW) / 2 + offX;     y = screenH - hudH - offY;           break;
            case BOTTOM_RIGHT:  x = screenW - hudW - offX;           y = screenH - hudH - offY;           break;
            case CUSTOM:
            default:            x = offX;                            y = offY;                            break;
        }
        return new int[] { x, y };
    }
}
