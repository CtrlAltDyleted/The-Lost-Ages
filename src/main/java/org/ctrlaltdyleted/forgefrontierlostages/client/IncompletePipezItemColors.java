package org.ctrlaltdyleted.forgefrontierlostages.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.ctrlaltdyleted.forgefrontierlostages.ForgeFrontierLostAges;

import java.util.List;

@Mod.EventBusSubscriber(modid = ForgeFrontierLostAges.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class IncompletePipezItemColors {
    private static final int MUTED_COLOR = 0xB0B0B0;

    private IncompletePipezItemColors() {}

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? MUTED_COLOR : 0xFFFFFF,
                ForgeFrontierLostAges.INCOMPLETE_UNIVERSAL_PIPE.get(),
                ForgeFrontierLostAges.INCOMPLETE_BASIC_UPGRADE.get(),
                ForgeFrontierLostAges.INCOMPLETE_IMPROVED_UPGRADE.get(),
                ForgeFrontierLostAges.INCOMPLETE_ADVANCED_UPGRADE.get(),
                ForgeFrontierLostAges.INCOMPLETE_ULTIMATE_UPGRADE.get());
    }

    @SubscribeEvent
    public static void tintUniversalPipe(ModelEvent.ModifyBakingResult event) {
        var modelId = new ModelResourceLocation(
                new ResourceLocation(ForgeFrontierLostAges.MOD_ID, "incomplete_universal_pipe"), "inventory");
        event.getModels().computeIfPresent(modelId, (id, model) -> new TintedModel(model));
    }

    private record TintedModel(BakedModel original) implements BakedModel {
        private static List<BakedQuad> tint(List<BakedQuad> quads) {
            return quads.stream().map(quad -> new BakedQuad(quad.getVertices(), 0,
                    quad.getDirection(), quad.getSprite(), quad.isShade(),
                    quad.hasAmbientOcclusion())).toList();
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return tint(original.getQuads(state, side, random));
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                         ModelData modelData, RenderType renderType) {
            return tint(original.getQuads(state, side, random, modelData, renderType));
        }

        @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return original.isGui3d(); }
        @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return original.isCustomRenderer(); }
        @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
        @Override public ItemTransforms getTransforms() { return original.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return original.getOverrides(); }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return original.getRenderTypes(stack, fabulous);
        }
    }
}
