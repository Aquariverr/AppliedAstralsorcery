package com.appliedastralsorcery.crystallizer;

import java.util.List;
import java.util.Objects;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEItemKey;
import appeng.client.render.BlockEntityRenderHelper;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import appeng.util.SettingsFrom;
import com.appliedastralsorcery.AppliedAstralsorcery;
import com.mojang.blaze3d.vertex.PoseStack;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class MELumenCrystallizerPart extends AEBasePart implements IGridTickable, WorldCrystallizerHost {
    private static final IPartModel MODEL = new PartModel(ResourceLocation.fromNamespaceAndPath(
            AppliedAstralsorcery.MOD_ID, "part/me_lumen_crystallizer"));
    private final WorldCrystallization crystallization = new WorldCrystallization();
    private ItemStack clientMarker = ItemStack.EMPTY;

    public MELumenCrystallizerPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(2)
                .addService(IGridTickable.class, this);
    }

    public static void registerModels() {
        getModels().forEach(model -> PartModels.registerModels(model.getModels()));
    }

    @appeng.items.parts.PartModels
    public static List<IPartModel> getModels() { return List.of(MODEL); }

    @Override public IPartModel getStaticModels() { return MODEL; }

    @Override public void getBoxes(IPartCollisionHelper helper) {
        // AE2 collision coordinates face south; static model coordinates face north.
        helper.addBox(1, 1, 14, 15, 15, 16);
        helper.addBox(4, 4, 13, 12, 12, 14);
    }

    @Override public int getLightLevel() { return 4; }

    @Override public BlockPos getCrystallizationTarget() {
        var side = Objects.requireNonNull(getSide(), "Crystallizer must be attached to a cable face");
        return getBlockEntity().getBlockPos().relative(side);
    }

    public ItemStack getMarker() {
        return isClientSide() ? clientMarker.copy() : crystallization.getMarker();
    }

    public Lumen markCatalyst(ItemStack stack) {
        var lumen = crystallization.markCatalyst(getLevel(), stack);
        if (lumen != null) changed();
        return lumen;
    }

    public void clearMarker() {
        crystallization.clearMarker();
        changed();
    }

    private void changed() {
        if (getHost() != null) {
            getHost().markForSave();
            getHost().markForUpdate();
        }
    }

    @Override public boolean onUseItemOn(ItemStack stack, Player player, InteractionHand hand, Vec3 pos) {
        if (player.isSpectator() || crystallization.findRecipe(getLevel(), stack) == null)
            return super.onUseItemOn(stack, player, hand, pos);
        if (!isClientSide()) {
            var lumen = markCatalyst(stack);
            if (lumen != null) player.displayClientMessage(Component.translatable(
                    "message.appliedas.crystallizer.marked", lumen.getHoverName(), stack.getHoverName()), true);
        }
        return true;
    }

    @Override public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (player.isSpectator() || !player.getMainHandItem().isEmpty()) return false;
        if (!isClientSide()) {
            if (player.isShiftKeyDown()) {
                clearMarker();
                player.displayClientMessage(Component.translatable("message.appliedas.crystallizer.cleared"), true);
            } else if (!getMarker().isEmpty()) {
                player.displayClientMessage(Component.translatable("message.appliedas.crystallizer.catalyst",
                        getMarker().getHoverName()), true);
            }
        }
        return true;
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false);
    }

    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        serverTick();
        return TickRateModulation.SAME;
    }

    public void serverTick() {
        if (!(getLevel() instanceof ServerLevel server) || !getMainNode().isOnline()) return;
        var grid = getMainNode().getGrid();
        if (grid != null && crystallization.tick(server, getCrystallizationTarget(),
                grid.getStorageService().getInventory(), IActionSource.ofMachine(this), server.random::nextInt)) {
            getHost().markForSave();
        }
    }

    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.put("crystallizer", crystallization.save(registries, true));
    }

    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        crystallization.load(data.getCompound("crystallizer"), registries, true);
        clientMarker = crystallization.getMarker();
    }

    @Override public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(data, crystallization.getMarker());
    }

    @Override public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean redraw = super.readFromStream(data);
        clientMarker = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
        return redraw;
    }

    @Override public void exportSettings(SettingsFrom mode, DataComponentMap.Builder output) {
        super.exportSettings(mode, output);
        if (getBlockEntity() != null && getLevel() != null) {
            var data = new CompoundTag();
            data.put("crystallizer", crystallization.save(getLevel().registryAccess(), mode == SettingsFrom.DISMANTLE_ITEM));
            output.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
    }

    @Override public void importSettings(SettingsFrom mode, DataComponentMap input, Player player) {
        super.importSettings(mode, input, player);
        var settings = input.get(DataComponents.CUSTOM_DATA);
        if (settings != null && getBlockEntity() != null && getLevel() != null) {
            var data = settings.copyTag();
            // Older block items stored this data directly rather than under the shared key.
            var payload = data.contains("crystallizer") ? data.getCompound("crystallizer") : data;
            crystallization.load(payload, getLevel().registryAccess(), mode == SettingsFrom.DISMANTLE_ITEM);
            changed();
        }
    }

    @Override public boolean requireDynamicRender() { return true; }

    @Override @OnlyIn(Dist.CLIENT)
    public void renderDynamic(float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var marker = AEItemKey.of(getMarker());
        var side = getSide();
        if (marker == null || side == null) return;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        BlockEntityRenderHelper.rotateToFace(pose, BlockOrientation.get(side, 0));
        pose.translate(0, 0, 0.501);
        BlockEntityRenderHelper.renderItem2d(pose, buffers, marker, 0.4F, light, getLevel());
        pose.popPose();
    }
}
