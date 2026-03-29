/**
 * Copyright (c) TheBreakery by MrBreakNFix
 *
 * @file ScrollPanelWidget.java
 */
package com.mrbreaknfix.ui_utils.gui.widget.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.mrbreaknfix.ui_utils.gui.BaseOverlay;
import com.mrbreaknfix.ui_utils.gui.GuiTheme;
import com.mrbreaknfix.ui_utils.gui.widget.*;
import com.mrbreaknfix.ui_utils.utils.CursorManager;
import com.mrbreaknfix.ui_utils.utils.GuiUtils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

import org.lwjgl.glfw.GLFW;

import static com.mrbreaknfix.ui_utils.UiUtils.mc;

public class ScrollPanelWidget extends Widget implements Scrollable, Typeable, ContainerWidget {

    private static final int SCROLL_BAR_WIDTH = 6;
    private static final int SCROLL_BAR_PADDING = 2;
    private static final double SCROLL_SMOOTHING = 0.01;
    private static final double SCROLL_STEP = 14;
    private final List<Widget> contentWidgets = new ArrayList<>();
    private final int paddingTop;
    private final int paddingRight;
    private final int paddingBottom;
    private final int paddingLeft;
    private Widget focusedContentWidget = null;
    private double scrollY = 0;
    private double targetScrollY = 0;
    private int contentHeight = 0;
    private double maxScrollY = 0;
    private boolean draggingScrollBar = false;
    private double scrollBarDragStartMouseY = 0;
    private double scrollBarDragStartScrollY = 0;

    public ScrollPanelWidget(
            String id, int x, int y, int width, int height, int padding, BaseOverlay overlay) {
        this(id, x, y, width, height, padding, padding, padding, padding, overlay);
    }

    public ScrollPanelWidget(
            String id,
            int x,
            int y,
            int width,
            int height,
            int paddingTop,
            int paddingRight,
            int paddingBottom,
            int paddingLeft,
            BaseOverlay overlay) {
        super(id, x, y, width, height, overlay);
        this.paddingTop = paddingTop;
        this.paddingRight = paddingRight;
        this.paddingBottom = paddingBottom;
        this.paddingLeft = paddingLeft;
    }

    public void addContent(Widget widget) {
        contentWidgets.add(widget);
        widget.setParent(this);
    }

    public void clearContent() {
        for (Widget contentWidget : contentWidgets) {
            contentWidget.onUnload();
        }
        contentWidgets.clear();
        focusedContentWidget = null;
        contentHeight = 0;
        recalculateScroll();
    }

    public void setContentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
        recalculateScroll();
    }

    public double getScrollY() {
        return scrollY;
    }

    /**
     * Sets the current vertical scroll position. The value will be clamped between 0 and the
     * maximum scrollable height. This will stop any smooth scrolling and jump directly to the
     * specified position.
     *
     * @param scrollY The new vertical scroll position.
     */
    public void setScrollY(double scrollY) {
        this.scrollY = MathHelper.clamp(scrollY, 0, maxScrollY);
        this.targetScrollY = this.scrollY;
    }

    public int getPadding() {
        return paddingLeft;
    }

    public int getContentWidth() {
        int contentWidth = width - paddingLeft - paddingRight;
        if (maxScrollY > 0) {
            contentWidth -= (SCROLL_BAR_WIDTH + SCROLL_BAR_PADDING);
        }
        return contentWidth;
    }

    private void recalculateScroll() {
        this.maxScrollY = Math.max(0, contentHeight - (height - (paddingTop + paddingBottom)));
        this.targetScrollY = MathHelper.clamp(this.targetScrollY, 0, maxScrollY);
        this.scrollY = MathHelper.clamp(this.scrollY, 0, maxScrollY);
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        recalculateScroll();
    }

    private Widget getHoveredWidget(double mouseX, double mouseY) {
        double localMouseX = mouseX - (this.x + paddingLeft);
        double localMouseY = mouseY - (this.y + paddingTop) + scrollY;

        for (int i = contentWidgets.size() - 1; i >= 0; i--) {
            Widget widget = contentWidgets.get(i);
            if (widget.isMouseOver(localMouseX, localMouseY)) {
                return widget;
            }
        }
        return null;
    }

    @Override
    public Widget getHoveredChild(double mouseX, double mouseY) {
        double localMouseX = mouseX - (this.x + paddingLeft);
        double localMouseY = mouseY - (this.y + paddingTop) + scrollY;

        for (int i = contentWidgets.size() - 1; i >= 0; i--) {
            Widget widget = contentWidgets.get(i);
            if (widget.isMouseOver(localMouseX, localMouseY)) {
                return widget;
            }
        }
        return null;
    }

    @Override
    public void renderDebugHighlight(DrawContext context, double mouseX, double mouseY) {
        GuiUtils.drawBorder(context, this.x, this.y, this.width, this.height, 0xFF00FF00);

        Widget hoveredChild = getHoveredChild(mouseX, mouseY);
        if (hoveredChild != null) {
            int screenX = this.x + this.paddingLeft + hoveredChild.getX();
            int screenY = this.y + this.paddingTop + hoveredChild.getY() - (int) this.scrollY;

            GuiUtils.drawBorder(
                    context,
                    screenX,
                    screenY,
                    hoveredChild.getWidth(),
                    hoveredChild.getHeight(),
                    0xFF00FF00);
            context.drawText(
                    mc.textRenderer,
                    hoveredChild.getId(),
                    screenX + 2,
                    screenY + 2,
                    0xFF00FF00,
                    false);
        }
    }

    @Override
    public void update(double mouseX, double mouseY, float delta) {
        if (draggingScrollBar) {
            int scrollBarHeight = height - (paddingTop + paddingBottom);
            int handleHeight =
                    (int)
                            Math.max(
                                    20,
                                    scrollBarHeight * ((float) scrollBarHeight / (contentHeight)));
            handleHeight = Math.min(scrollBarHeight, handleHeight);
            double scrollableTrackHeight = scrollBarHeight - handleHeight;

            if (scrollableTrackHeight > 0) {
                double mouseDelta = mouseY - scrollBarDragStartMouseY;
                double scrollDelta = (mouseDelta / scrollableTrackHeight) * maxScrollY;
                scrollY = scrollBarDragStartScrollY + scrollDelta;
                scrollY = MathHelper.clamp(scrollY, 0, maxScrollY);
                targetScrollY = scrollY;
            }
        } else {
            if (Math.abs(targetScrollY - scrollY) > 0.5) {
                double factor = SCROLL_SMOOTHING * (delta * 60.0); // Normalize to ~60fps
                factor = Math.min(1.0, factor);
                scrollY += (targetScrollY - scrollY) * factor;
            } else {
                scrollY = targetScrollY;
            }
        }

        double localMouseX = mouseX - (this.x + paddingLeft);
        double localMouseY = mouseY - (this.y + paddingTop) + scrollY;
        for (Widget widget : contentWidgets) {
            widget.update(localMouseX, localMouseY, delta);
        }

        if (isMouseOver(mouseX, mouseY) && !isMouseOverScrollBar(mouseX, mouseY)) {
            Widget hoveredWidget = getHoveredWidget(mouseX, mouseY);
            if (hoveredWidget != null) {
                if (hoveredWidget instanceof Typeable && hoveredWidget.shouldChangeCursor())
                    getOverlay().changeCursor(CursorManager.IBEAM);
                else if (hoveredWidget instanceof HandCursor && hoveredWidget.shouldChangeCursor())
                    getOverlay().changeCursor(CursorManager.HAND);
                else getOverlay().changeCursor(CursorManager.ARROW);
            } else {
                getOverlay().changeCursor(CursorManager.ARROW);
            }
        }
    }

    @Override
    public void render(
            DrawContext context, Screen screen, double mouseX, double mouseY, float deltaTicks) {
        if (!isVisible()) return;

        context.fill(x, y, x + width, y + height, GuiTheme.WIDGET_BACKGROUND);
        GuiUtils.drawBorder(context, x, y, width, height, GuiTheme.WIDGET_BORDER);

        final int contentRenderX = x + paddingLeft;
        final int contentRenderY = y + paddingTop;
        final int contentRenderWidth = getContentWidth();
        final int contentRenderHeight = height - (paddingTop + paddingBottom);

        context.enableScissor(
                contentRenderX,
                contentRenderY,
                contentRenderX + contentRenderWidth,
                contentRenderY + contentRenderHeight);

        context.createNewRootLayer();

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) contentRenderX, (float) (contentRenderY - scrollY));

        double localMouseX = mouseX - contentRenderX;
        double localMouseY = mouseY - contentRenderY + scrollY;

        for (Widget widget : contentWidgets) {
            widget.render(context, screen, localMouseX, localMouseY, deltaTicks);
        }

        context.getMatrices().popMatrix();
        context.disableScissor();

        if (maxScrollY > 0) {
            int scrollBarX = x + width - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
            int scrollBarY = y + paddingTop;
            int scrollBarHeight = height - (paddingTop + paddingBottom);

            context.fill(
                    scrollBarX,
                    scrollBarY,
                    scrollBarX + SCROLL_BAR_WIDTH,
                    scrollBarY + scrollBarHeight,
                    0x55000000);

            int handleHeight =
                    (int)
                            Math.max(
                                    20,
                                    scrollBarHeight * ((float) scrollBarHeight / (contentHeight)));
            handleHeight = Math.min(scrollBarHeight, handleHeight);

            int handleY = scrollBarY;
            if (maxScrollY > 0) {
                handleY += (int) ((scrollY / maxScrollY) * (scrollBarHeight - handleHeight));
            }

            int handleColor =
                    isMouseOverScrollBar(mouseX, mouseY) || draggingScrollBar
                            ? 0xFFFFFFFF
                            : 0xAAFFFFFF;
            context.fill(
                    scrollBarX,
                    handleY,
                    scrollBarX + SCROLL_BAR_WIDTH,
                    handleY + handleHeight,
                    handleColor);
        }
    }

    private boolean isMouseOverScrollBar(double mouseX, double mouseY) {
        if (maxScrollY <= 0) return false;
        int scrollBarX = x + width - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
        int scrollBarY = y + paddingTop;
        int scrollBarHeight = height - (paddingTop + paddingBottom);
        return mouseX >= scrollBarX
                && mouseX <= scrollBarX + SCROLL_BAR_WIDTH
                && mouseY >= scrollBarY
                && mouseY <= scrollBarY + scrollBarHeight;
    }

    @Override
    public void onMouseClick(double mouseX, double mouseY, double button) {
        if (!isMouseOver(mouseX, mouseY)) return;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isMouseOverScrollBar(mouseX, mouseY)) {
            draggingScrollBar = true;
            scrollBarDragStartMouseY = mouseY;
            scrollBarDragStartScrollY = scrollY;
            return;
        }

        Widget target = getHoveredWidget(mouseX, mouseY);

        if (target != null) {
            getOverlay().handleFocus(target);
            focusedContentWidget = target;
            target.onMouseClick(
                    mouseX - (x + paddingLeft), mouseY - (y + paddingTop) + scrollY, button);

            if (target instanceof Noisy) {
                mc.getSoundManager()
                        .play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        } else {
            if (focusedContentWidget != null) {
                focusedContentWidget.setFocused(false);
                focusedContentWidget = null;
            }
            getOverlay().handleFocus(null);
        }
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, double button) {
        draggingScrollBar = false;
        double localMouseX = mouseX - (x + paddingLeft);
        double localMouseY = mouseY - (y + paddingTop) + scrollY;
        for (Widget widget : contentWidgets) {
            widget.onMouseRelease(localMouseX, localMouseY, button);
        }
    }

    @Override
    public boolean onMouseScroll(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY) || isMouseOverScrollBar(mouseX, mouseY)) return false;
        targetScrollY -= verticalAmount * SCROLL_STEP;
        targetScrollY = MathHelper.clamp(targetScrollY, 0, maxScrollY);
        return true;
    }

    @Override
    public boolean onKey(int key, int scancode, int modifiers) {
        if (focusedContentWidget != null
                && focusedContentWidget.isFocused()
                && focusedContentWidget instanceof Typeable typeable) {
            return typeable.onKey(key, scancode, modifiers);
        }
        return false;
    }

    @Override
    public boolean onChar(int codePoint, int modifiers) {
        if (focusedContentWidget != null
                && focusedContentWidget.isFocused()
                && focusedContentWidget instanceof Typeable typeable) {
            return typeable.onChar(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean onInput(Function<String, String> factory) {
        return false;
    }

    @Override
    public void onUnload() {
        super.onUnload();
        clearContent();
    }
}
