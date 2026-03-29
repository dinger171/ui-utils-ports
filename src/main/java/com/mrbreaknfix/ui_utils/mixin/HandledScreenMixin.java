/**
 * Copyright (c) TheBreakery by MrBreakNFix
 *
 * @file HandledScreenMixin.java
 */
package com.mrbreaknfix.ui_utils.mixin;

import com.mrbreaknfix.ui_utils.gui.widget.Typeable;
import com.mrbreaknfix.ui_utils.utils.ScreenCommandSlotManager;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mrbreaknfix.ui_utils.UiUtils.*;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Unique private static boolean isOverSlot = false;

    @Inject(at = @At("RETURN"), method = "render")
    private void renderOverlayOnTop(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        final int zIndex = 500;

        if (slotManager.isPicking()) {
            if (isOverSlot) {
                int x1 = mouseX - 8;
                int y1 = mouseY - 8;
                int x2 = mouseX + 8;
                int y2 = mouseY + 8;
                context.fill(RenderPipelines.GUI, x1, y1, x2, y2, zIndex);
            } else {
                int x1 = mouseX - 8;
                int y1 = mouseY - 8;
                int x2 = mouseX + 8;
                int y2 = mouseY + 8;
                context.fill(RenderPipelines.GUI, x1, y1, x2, y2, zIndex);
            }
            isOverSlot = false;
        }
    }

    @Inject(
            method =
                    "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("RETURN"),
            cancellable = true)
    private void onClickSlot(
            Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (slotManager.isPicking()) {
            if (slot != null) {
                slotManager.setHighlightedSlotID(slot.id);
                slotManager.setShouldStopPicking(true);
                ci.cancel();
            } else {
                slotManager.setShouldStopPicking(true);
            }
        }
    }

    @Inject(method = "drawSlotHighlightFront", at = @At("RETURN"))
    private void onDrawSlotHighlight(DrawContext context, CallbackInfo ci) {
        isOverSlot = true;
    }

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void onDrawSlot(
            DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ScreenCommandSlotManager.drawHighlightOnSlot(context, slot);
        ScreenCommandSlotManager.drawSlotId(context, slot);
    }

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void drawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (slot.id == slotManager.getHighlightedSlotID()
                && slotManager.shouldRenderHighlightedSlot()) {
            slotManager.drawHighlightedOnSlot(context, slot);
        }
        if (slotManager.shouldDrawSlotIDs()) {
            slotManager.drawSlotID(context, slot);
        }
    }

    @Inject(at = @At("HEAD"), method = "keyPressed", cancellable = true)
    public void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (mc.options.inventoryKey.matchesKey(input)) {
            if (overlay.focusedWidget instanceof Typeable) {
                cir.setReturnValue(false);
            }
        }
    }
}
