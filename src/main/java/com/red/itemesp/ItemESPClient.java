/*
 * Copyright (c) 2014-2022 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.red.itemesp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;

public class ItemESPClient implements ClientModInitializer {
    public static final Logger logger = LogManager.getLogger("ItemESP");
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    static boolean boxesEnabled = false;
    static Color color = Color.YELLOW;
    static float extrasize = 0;
    private static final ArrayList<ItemEntity> items = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        logger.info("ItemESP Loaded.");
        ClientTickEvents.END_CLIENT_TICK.register(ItemESPClient::onUpdate);
        WorldRenderEvents.AFTER_ENTITIES.register(ItemESPClient::onAfterEntitiesRender);

        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("itemesp").executes(context -> {
                boxesEnabled = !boxesEnabled;
                context.getSource().sendFeedback(new LiteralText(String.format("%s ItemESP", boxesEnabled ? "Enabled" : "Disabled")));
                return 1;
            })
            .then(ClientCommandManager.literal("color")
                .then(ClientCommandManager.argument("color", ColorArgumentType.color()).executes(context -> {
                    color = Color.decode(String.valueOf(context.getArgument("color", Formatting.class).getColorValue()));
                    return 1;
                }))
            )
            .then(ClientCommandManager.literal("extrasize")
                .then(ClientCommandManager.argument("extrasize", FloatArgumentType.floatArg()).executes(context -> {
                    extrasize = context.getArgument("extrasize", float.class);
                    return 1;
                }))
            )
        );
    }

    public static void onUpdate(MinecraftClient client) {
        items.clear();
        if (client.world == null)
            return;
        for (Entity entity : client.world.getEntities())
            if (entity instanceof ItemEntity)
                items.add((ItemEntity)entity);
    }

    public static float[] getColorF(Color color)
    {
        float red = color.getRed() / 255F;
        float green = color.getGreen() / 255F;
        float blue = color.getBlue() / 255F;
        return new float[]{red, green, blue};
    }

    public static Vec3d getCameraPos()
    {
        return mc.getBlockEntityRenderDispatcher().camera.getPos();
    }

    public static BlockPos getCameraBlockPos()
    {
        return mc.getBlockEntityRenderDispatcher().camera.getBlockPos();
    }

    public static void applyRegionalRenderOffset(MatrixStack matrixStack)
    {
        Vec3d camPos = getCameraPos();
        BlockPos blockPos = getCameraBlockPos();

        int regionX = (blockPos.getX() >> 9) * 512;
        int regionZ = (blockPos.getZ() >> 9) * 512;

        matrixStack.translate(regionX - camPos.x, -camPos.y, regionZ - camPos.z);
    }

    public static void onAfterEntitiesRender(WorldRenderContext wrc) {
        // GL settings
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        wrc.matrixStack().push();
        applyRegionalRenderOffset(wrc.matrixStack());

        BlockPos camPos = getCameraBlockPos();
        int regionX = (camPos.getX() >> 9) * 512;
        int regionZ = (camPos.getZ() >> 9) * 512;

        renderBoxes(wrc.matrixStack(), wrc.tickDelta(), regionX, regionZ);
        wrc.matrixStack().pop();

        // GL resets
        RenderSystem.setShaderColor(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private static void renderBoxes(MatrixStack matrixStack, double partialTicks, int regionX, int regionZ)
    {
        for (ItemEntity e : items)
        {
            matrixStack.push();

            matrixStack.translate(e.prevX + (e.getX() - e.prevX) * partialTicks - regionX, e.prevY + (e.getY() - e.prevY) * partialTicks, e.prevZ + (e.getZ() - e.prevZ) * partialTicks - regionZ);

            if (boxesEnabled)
            {
                matrixStack.push();
                matrixStack.scale(e.getWidth() + extrasize, e.getHeight() + extrasize, e.getWidth() + extrasize);

                GL11.glEnable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                float[] colorF = getColorF(color);
                RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.5F);
                drawOutlinedBox(new Box(-0.5, 0, -0.5, 0.5, 1, 0.5), matrixStack);

                matrixStack.pop();
            }

            matrixStack.pop();
        }
    }

    public static void drawOutlinedBox(Box bb, MatrixStack matrixStack)
    {
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionShader);

        bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).next();
        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).next();

        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).next();
        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).next();

        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).next();
        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).next();

        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).next();
        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).next();

        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ).next();
        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).next();

        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ).next();
        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).next();

        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ).next();
        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).next();

        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ).next();
        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).next();

        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).next();
        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).next();

        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ).next();
        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).next();

        bufferBuilder.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ).next();
        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).next();

        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ).next();
        bufferBuilder.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ).next();
        bufferBuilder.end();
        BufferRenderer.draw(bufferBuilder);
    }
}
