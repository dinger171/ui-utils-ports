/**
 * Copyright (c) TheBreakery by MrBreakNFix
 *
 * @file BaseOverlay.java
 */
package com.mrbreaknfix.ui_utils.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.mrbreaknfix.ui_utils.UiUtils;
import com.mrbreaknfix.ui_utils.gui.widget.*;
import com.mrbreaknfix.ui_utils.persistance.OverlayPersistence;
import com.mrbreaknfix.ui_utils.utils.CursorManager;
import com.mrbreaknfix.ui_utils.utils.GuiUtils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import org.lwjgl.glfw.GLFW;

import static com.mrbreaknfix.ui_utils.UiUtils.eventManager;
import static com.mrbreaknfix.ui_utils.UiUtils.mc;

public abstract class BaseOverlay {
    private final List<Widget> widgets;
    private final List<Widget> topLevelWidgets;
    public File saveFile;
    public Widget focusedWidget;
    public boolean enabled = true;

    private Widget draggingWidget = null;
    private Widget lastDraggedWidget = null;
    private double lastMouseX = -1, lastMouseY = -1;
    public static int lastCursor = -1;

    public BaseOverlay(File file) {
        this.widgets = new ArrayList<>();
        this.topLevelWidgets = new ArrayList<>();
        saveFile = file;
        if (saveFile != null) {
            OverlayPersistence.loadFromFile(saveFile);
        }
        UiUtils.LOGGER.info("BaseOverlay initialized: " + this.getClass().getSimpleName());
    }

    public void addTopLevelWidget(Widget widget) {
        if (!topLevelWidgets.contains(widget)) {
            topLevelWidgets.add(widget);
        }
    }

    public void removeTopLevelWidget(Widget widget) {
        if (focusedWidget != null
                && (focusedWidget == widget || isDescendant(widget, focusedWidget))) {
            focusedWidget.setFocused(false);
            focusedWidget = null;
        }
        topLevelWidgets.remove(widget);
    }

    public void clearTopLevelWidgets() {
        if (focusedWidget != null
                && topLevelWidgets.stream()
                        .anyMatch(w -> w == focusedWidget || isDescendant(w, focusedWidget))) {
            focusedWidget.setFocused(false);
            focusedWidget = null;
        }
        topLevelWidgets.clear();
    }

    private boolean isDescendant(Widget parent, Widget potentialChild) {
        for (Widget child : parent.getChildren()) {
            if (child == potentialChild || isDescendant(child, potentialChild)) {
                return true;
            }
        }
        return false;
    }

    public void resetCursor() {
        changeCursor(CursorManager.ARROW);
    }

    public void close() {
        eventManager.removeListener(this);
        clearWidgets();
        clearTopLevelWidgets();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void update(Screen screen, double mouseX, double mouseY, float deltaTicks) {
        if (!enabled) return;
        getStreamOfAllWidgets()
                .forEach(
                        widget -> {
                            if (widget.isVisible()) widget.update(mouseX, mouseY, deltaTicks);
                        });

        if (draggingWidget != null) {
            boolean isRightClickHeld =
                    GLFW.glfwGetMouseButton(
                                    mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                            == GLFW.GLFW_PRESS;
            if (isRightClickHeld) {
                if (lastMouseX != -1 && lastMouseY != -1 && draggingWidget.isMovable()) {
                    double frameDeltaX = mouseX - lastMouseX;
                    double frameDeltaY = mouseY - lastMouseY;
                    draggingWidget.move(frameDeltaX, frameDeltaY);
                    draggingWidget.bound(screen.width, screen.height);
                }
            } else {
                if (draggingWidget.isMovable()) saveWidgetPosition(draggingWidget);
                lastDraggedWidget = draggingWidget;
                draggingWidget = null;
            }
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public boolean onMouseScroll(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!enabled) return false;

        // find topmost widget
        List<Widget> sortedWidgets =
                getStreamOfAllWidgets()
                        .filter(Widget::isVisible)
                        .sorted(getDrawingOrderComparator())
                        .toList();

        for (int i = sortedWidgets.size() - 1; i >= 0; i--) {
            Widget widget = sortedWidgets.get(i);
            if (widget.isMouseOver(mouseX, mouseY)) {
                if (widget instanceof Scrollable scrollable) {
                    if (scrollable.onMouseScroll(
                            mouseX, mouseY, horizontalAmount, verticalAmount)) {
                        return true; // Event consumed
                    }
                }
                // The scroll happened over this widget, even if it's not scrollable.
                // Prevent widgets underneath from scrolling.
                return false;
            }
        }
        return false;
    }

    private Comparator<Widget> getDrawingOrderComparator() {
        return (w1, w2) -> {
            Widget root1 = w1.getDeepestParent();
            Widget root2 = w2.getDeepestParent();
            boolean isTop1 = topLevelWidgets.contains(root1);
            boolean isTop2 = topLevelWidgets.contains(root2);

            // 1. Prioritize top-level widgets. A "true" value is greater, so it will be sorted
            // later.
            if (isTop1 != isTop2) {
                return Boolean.compare(isTop1, isTop2);
            }

            // 2. If they are in the same tree, prioritize children over parents.
            if (root1 == root2) {
                if (isDescendant(w1, w2)) { // w2 is a child of w1
                    return -1; // w1 should be drawn before w2 (w2 is on top)
                }
                if (isDescendant(w2, w1)) { // w1 is a child of w2
                    return 1; // w2 should be drawn before w1 (w1 is on top)
                }
            }

            // 3. For siblings or widgets in different trees (of same top-level status), use
            // Z-index.
            return Integer.compare(w1.getZIndex(), w2.getZIndex());
        };
    }

    public void render(
            DrawContext context, Screen screen, double mouseX, double mouseY, float deltaTicks) {
        if (!enabled) return;
        update(screen, mouseX, mouseY, deltaTicks);

        // Get all visible widgets and sort them using the unified priority logic.
        // The list is sorted from bottom-to-top.
        List<Widget> widgetsToRender =
                getStreamOfAllWidgets()
                        .filter(Widget::isVisible)
                        .sorted(getDrawingOrderComparator())
                        .toList();

        // Render widgets in order (bottom-to-top).
        for (Widget widget : widgetsToRender) {
            widget.render(context, screen, mouseX, mouseY, deltaTicks);
        }

        boolean hovered = false;
        // Check for hover/cursor changes by iterating backwards (top-to-bottom).
        for (int i = widgetsToRender.size() - 1; i >= 0; i--) {
            Widget widget = widgetsToRender.get(i);

            if (widget.isMouseOver(mouseX, mouseY)) {
                hovered = true; //  topmost widge found.

                if (widget instanceof ContainerWidget container) {
                    // For containers, cursor logic is handled in their update().
                    if (UiUtils.isDevModeEnabled) {
                        container.renderDebugHighlight(context, mouseX, mouseY);
                    }
                } else {
                    // For standard widgets, handle cursor and debug highlighting here.
                    if (widget instanceof Typeable && widget.shouldChangeCursor())
                        changeCursor(CursorManager.IBEAM);
                    else if (widget instanceof HandCursor && widget.shouldChangeCursor())
                        changeCursor(CursorManager.HAND);
                    else changeCursor(CursorManager.ARROW);

                    if (UiUtils.isDevModeEnabled) {
                        GuiUtils.drawBorder(
                                context,
                                widget.x,
                                widget.y,
                                widget.width,
                                widget.height,
                                0xFF00FF00);
                        context.drawText(
                                mc.textRenderer,
                                widget.getId(),
                                widget.x + 2,
                                widget.y + 2,
                                0xFF00FF00,
                                false);
                    }
                }

                break;
            }
        }

        if (!hovered && draggingWidget == null) resetCursor();
    }

    public void changeCursor(int cursorType) {
        if (lastCursor != cursorType) {
            lastCursor = cursorType;
            CursorManager.setCursor(cursorType);
        }
    }

    public boolean onMouseClick(double mouseX, double mouseY, int action, double button) {
        if (!enabled) return false;

        // 1. Get all visible widgets, sorted with the highest priority (topmost) widget last.
        List<Widget> sortedWidgets =
                getStreamOfAllWidgets()
                        .filter(Widget::isVisible)
                        .sorted(getDrawingOrderComparator())
                        .toList();

        // 2. Find the topmost widget under the cursor by iterating backwards.
        Widget targetWidget = null;
        for (int i = sortedWidgets.size() - 1; i >= 0; i--) {
            Widget widget = sortedWidgets.get(i);
            if (widget.isMouseOver(mouseX, mouseY)) {
                targetWidget = widget;
                break;
            }
        }

        // 3. Handle the click event.
        if (action == GLFW.GLFW_PRESS) {
            // If a widget was clicked
            if (targetWidget != null) {
                // Handle right-click drag
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    draggingWidget = targetWidget.getDeepestParent();
                    // Bring the entire tree to the front of its group (top-level or normal)
                    if (topLevelWidgets.contains(draggingWidget)) {
                        topLevelWidgets.remove(draggingWidget);
                        topLevelWidgets.add(draggingWidget);
                    } else {
                        removeWidget(draggingWidget);
                        addWidget(draggingWidget);
                    }
                    return true;
                }

                // Handle left-click focus and action
                handleFocus(targetWidget);
                targetWidget.onMouseClick(mouseX, mouseY, button);
                if (targetWidget instanceof Noisy) {
                    mc.getSoundManager()
                            .play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true; // Consume the event
            }
            // If no widget was clicked (clicked on empty space)
            else {
                if (focusedWidget != null) {
                    focusedWidget.setFocused(false);
                    focusedWidget = null;
                }
            }
        } else if (action == GLFW.GLFW_RELEASE) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && draggingWidget != null) {
                // Drag release is handled in update(), but we consume the event here
                return true;
            }
            sortedWidgets.forEach(w -> w.onMouseRelease(mouseX, mouseY, button));
        }

        return false;
    }

    public void handleFocus(Widget widget) {
        if (focusedWidget == widget) return;
        if (focusedWidget != null) focusedWidget.setFocused(false);

        focusedWidget = widget;

        if (widget != null) {
            widget.setFocused(true);

            Widget root = widget.getDeepestParent();
            if (topLevelWidgets.contains(root)) {
                topLevelWidgets.remove(root);
                topLevelWidgets.add(root);
            } else {
                removeWidget(root);
                addWidget(root);
            }
        }
    }

    public boolean onChar(int codePoint, int modifiers) {
        if (!enabled || focusedWidget == null || !focusedWidget.isVisible()) return false;
        if (focusedWidget instanceof Typeable typeable) {
            return typeable.onChar(codePoint, modifiers);
        }
        return false;
    }

    public boolean onKey(int key, int scancode, int action, int modifiers) {
        if (!enabled || action != GLFW.GLFW_PRESS) return false;
        if (focusedWidget instanceof Typeable typeable && focusedWidget.isVisible()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (typeable.onKey(key, scancode, modifiers)) return true;
                // If not handled, unfocus
                focusedWidget.setFocused(false);
                focusedWidget = null;
                return true;
            }
            return typeable.onKey(key, scancode, modifiers);
        }

        if (lastDraggedWidget != null && lastDraggedWidget.isMovable()) {
            boolean moved = false;
            switch (key) {
                case GLFW.GLFW_KEY_UP -> {
                    lastDraggedWidget.move(0, -1);
                    moved = true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    lastDraggedWidget.move(0, 1);
                    moved = true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    lastDraggedWidget.move(-1, 0);
                    moved = true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    lastDraggedWidget.move(1, 0);
                    moved = true;
                }
            }
            if (moved) {
                saveWidgetPosition(lastDraggedWidget);
                return true;
            }
        }
        return false;
    }

    private Stream<Widget> getStreamOfAllWidgets() {
        return Stream.concat(getAllWidgets().stream(), getAllTopLevelWidgets().stream());
    }

    // Helper to get all top-level widgets and their children
    private List<Widget> getAllTopLevelWidgets() {
        List<Widget> all = new ArrayList<>();
        for (Widget widget : topLevelWidgets) {
            all.add(widget);
            addAllChildren(widget, all);
        }
        return all;
    }

    private List<Widget> getAllWidgets() {
        List<Widget> all = new ArrayList<>();
        for (Widget widget : widgets) {
            all.add(widget);
            addAllChildren(widget, all);
        }
        return all;
    }

    private void addAllChildren(Widget parent, List<Widget> all) {
        for (Widget child : parent.getChildren()) {
            all.add(child);
            addAllChildren(child, all);
        }
    }

    public void addWidget(Widget widget) {
        widgets.add(widget);
    }

    public void removeWidget(Widget widget) {
        widgets.remove(widget);
    }

    public void saveWidgetPosition(Widget widget) {
        if (saveFile != null) {
            OverlayPersistence.savePosition(widget.getId(), widget.x, widget.y, saveFile);
        }
    }

    void loadWidgetPositions() {
        if (saveFile == null) return;
        for (Widget widget : widgets) {
            OverlayPersistence.restoreWidgetPosition(
                    widget.getId(), widget, widget.x, widget.y, saveFile);
        }
    }

    public void clearWidgets() {
        if (focusedWidget != null && widgets.contains(focusedWidget.getDeepestParent())) {
            focusedWidget.setFocused(false);
            focusedWidget = null;
        }
        for (Widget widget : widgets) widget.onUnload();
        widgets.clear();
    }
}
