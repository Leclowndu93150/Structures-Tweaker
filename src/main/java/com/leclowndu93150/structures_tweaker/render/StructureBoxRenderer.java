package com.leclowndu93150.structures_tweaker.render;

import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT, modid = StructuresTweaker.MODID)
public class StructureBoxRenderer {
    private static final Map<ResourceLocation, BoundingBox> boxes = new HashMap<>();
    private static boolean isEnabled = false;

    private static final RenderType STRUCTURE_LINES = RenderType.create("structure_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                    //.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(new RenderStateShard.LayeringStateShard("view_offset_z_layering",
                            () -> {
                                PoseStack posestack = RenderSystem.getModelViewStack();
                                posestack.pushPose();
                                posestack.scale(0.99975586F, 0.99975586F, 0.99975586F);
                                RenderSystem.applyModelViewMatrix();
                            },
                            () -> {
                                PoseStack posestack = RenderSystem.getModelViewStack();
                                posestack.popPose();
                                RenderSystem.applyModelViewMatrix();
                            }))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("translucent_transparency", () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                    }, () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    }))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("always", 519))
                    .createCompositeState(false)
    );

    public static void setEnabled(boolean enabled) {
        isEnabled = enabled;
        if (!enabled) {
            clearBoxes();
        }
    }

    public static void addBox(ResourceLocation id, BoundingBox box) {
        boxes.put(id, box);
    }

    public static void clearBoxes() {
        boxes.clear();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!isEnabled || !event.getLevel().isClientSide()) return;

        ChunkAccess chunk = event.getChunk();
        chunk.getAllStarts().forEach((structure, structureStart) -> {
            if (structureStart != null) {
                structureStart.getBoundingBox();
                ResourceLocation id = event.getLevel().registryAccess()
                        .registryOrThrow(Registries.STRUCTURE)
                        .getKey(structure);
                if (id != null) {
                    boxes.put(id, structureStart.getBoundingBox());
                }
            }
        });
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!isEnabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        var buffer = Minecraft.getInstance().renderBuffers().bufferSource();

        boxes.forEach((id, box) -> {
            AABB aabb = new AABB(
                    box.minX() - event.getCamera().getPosition().x,
                    box.minY() - event.getCamera().getPosition().y,
                    box.minZ() - event.getCamera().getPosition().z,
                    box.maxX() + 1 - event.getCamera().getPosition().x,
                    box.maxY() + 1 - event.getCamera().getPosition().y,
                    box.maxZ() + 1 - event.getCamera().getPosition().z
            );

            RenderSystem.lineWidth(10F);
            VertexConsumer builder = buffer.getBuffer(STRUCTURE_LINES);
            LevelRenderer.renderLineBox(poseStack, builder, aabb, 1.0F, 0.0F, 0.0F, 1.0F);
        });

        buffer.endBatch(STRUCTURE_LINES);
    }
}