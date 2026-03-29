/**
 * Copyright (c) TheBreakery by MrBreakNFix
 *
 * @file ChatWidget.java
 */
package com.mrbreaknfix.ui_utils.gui.widget.widgets;

import com.mrbreaknfix.ui_utils.gui.BaseOverlay;
import com.mrbreaknfix.ui_utils.utils.UIActions;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import static com.mrbreaknfix.ui_utils.UiUtils.mc;

public class ChatWidget extends TextInputWidget {
    public ChatWidget(String id, int x, int y, int width, int height, BaseOverlay overlay) {
        super(id, x, y, width, height, overlay);
        this.setDefaultInput("Input Chat / Command");
    }

    @Override
    public void onEnter() {
        mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (this.getTextOrDefault().isEmpty() || this.getInput().equals("Input Chat / Command"))
            return;
        if (mc.player == null) return;
        mc.execute(() -> UIActions.chat(this.getTextOrDefault()));
        this.setDefaultInput(this.getTextOrDefault());
        this.setText("");
    }
}
