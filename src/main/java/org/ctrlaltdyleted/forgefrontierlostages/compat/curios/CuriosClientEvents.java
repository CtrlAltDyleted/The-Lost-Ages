package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

import com.ultramega.ae2insertexportcard.container.UpgradeContainerMenu;
import com.ultramega.ae2insertexportcard.screen.UpgradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shows Curios' equipped slots beside the Export Card while its filter menu stays open. */
final class CuriosClientEvents {
    private static final ResourceLocation INVENTORY = new ResourceLocation("curios", "textures/gui/inventory.png");
    private static final ResourceLocation EMPTY_SLOT = new ResourceLocation("curios", "slot/empty_curio_slot");
    private static final ResourceLocation XMARK = new ResourceLocation("forgefrontierlostages", "textures/gui/export_slot_xmark.png");
    private static final ResourceLocation CARD_PANEL = new ResourceLocation("ae2insertexportcard", "textures/gui/upgrade.png");
    private static final ResourceLocation AE2_BACKGROUND = new ResourceLocation("ae2", "textures/guis/background.png");
    private static final int CELL = 18;
    private UpgradeScreen current;
    private ImageButton button;
    private boolean open;
    private int firstColumn;
    private List<CuriosSelections.Selection> selected = new ArrayList<>();

    private boolean active(Screen screen) {
        return screen instanceof UpgradeScreen upgrade && upgrade.getMenu().getType() == UpgradeContainerMenu.TYPE_EXPORT;
    }

    private record Layout(int x, int y, int rows, int columns, int width, int height) {
        int cellX(int column) { return x + 4 + column * CELL; }
        int cellY(int row) { return y + 4 + row * CELL; }
    }

    private Layout layout(UpgradeScreen screen, int count) {
        int y = Math.max(2, screen.getGuiTop());
        int rows = Math.max(1, Math.min(8, (screen.height - y - 8) / CELL));
        int needed = Math.max(1, (count + rows - 1) / rows);
        // Leave the guide button's strip clear at the upper left of the card.
        int leftSpace = screen.getGuiLeft() - 20;
        int columns = Math.max(1, Math.min(needed, (leftSpace - 8) / CELL));
        int width = 8 + columns * CELL;
        int x = screen.getGuiLeft() - width - 18;
        x = Math.max(2, Math.min(x, screen.width - width - 2));
        return new Layout(x, y, rows, columns, width, 8 + rows * CELL);
    }

    private void positionButton(UpgradeScreen screen) {
        if (button == null) return;
        // AE2's guide button occupies the upper left edge; center this below it.
        button.setX(Math.max(2, screen.getGuiLeft() - 14));
        button.setY(Math.min(screen.height - 16, screen.getGuiTop() + 23));
    }

    @SubscribeEvent
    public void init(ScreenEvent.Init.Post event) {
        if (!CuriosIntegration.enabled()) return;
        try {
            initScreen(event);
        } catch (LinkageError | RuntimeException error) {
            CuriosIntegration.disable(error);
        }
    }

    private void initScreen(ScreenEvent.Init.Post event) {
        if (!active(event.getScreen())) return;
        UpgradeScreen screen = (UpgradeScreen) event.getScreen();
        current = screen;
        open = false;
        firstColumn = 0;
        ItemStack terminal = ((appeng.helpers.WirelessTerminalMenuHost) screen.getMenu().getHost()).getItemStack();
        CuriosSelections.Card card = CuriosSelections.find(terminal);
        selected = card == null ? new ArrayList<>() : CuriosSelections.read(card.stack());
        button = new ImageButton(0, 0, 14, 14, 50, 0, 14, INVENTORY, pressed -> open = !open) {
            @Override
            public boolean isHoveredOrFocused() {
                return isHovered() || open;
            }
        };
        button.setTooltip(Tooltip.create(Component.literal("Curios Equipment")));
        positionButton(screen);
        event.addListener(button);
    }

    @SubscribeEvent
    public void beforeRender(ScreenEvent.Render.Pre event) {
        if (CuriosIntegration.enabled() && event.getScreen() == current) positionButton(current);
    }

    private List<Slot> slots() {
        List<Slot> result = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return result;
        CuriosApi.getCuriosInventory(minecraft.player).ifPresent(curios -> {
            curios.getCurios().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                ICurioStacksHandler handler = entry.getValue();
                if (!handler.isVisible()) return;
                for (int index = 0; index < handler.getSlots(); index++) {
                    result.add(new Slot(entry.getKey(), index, handler.getStacks().getStackInSlot(index)));
                }
            });
        });
        return result;
    }

    private record Slot(String type, int index, ItemStack item) {}

    @SubscribeEvent
    public void render(ScreenEvent.Render.Post event) {
        if (!CuriosIntegration.enabled()) return;
        try {
            renderPanel(event);
        } catch (LinkageError | RuntimeException error) {
            CuriosIntegration.disable(error);
            open = false;
        }
    }

    private void renderPanel(ScreenEvent.Render.Post event) {
        if (!open || event.getScreen() != current) return;
        List<Slot> slots = slots();
        Layout panel = layout(current, slots.size());
        firstColumn = Math.min(firstColumn,
                Math.max(0, (slots.size() + panel.rows() - 1) / panel.rows() - panel.columns()));
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.flush();
        // These AE2/card textures pick up the same active resource pack as the Export Card inventory.
        renderBackground(graphics, panel);
        renderSlotGrid(graphics, panel);
        int hovered = -1;
        for (int column = 0; column < panel.columns(); column++) {
            for (int row = 0; row < panel.rows(); row++) {
                int index = (firstColumn + column) * panel.rows() + row;
                if (index >= slots.size()) break;
                Slot slot = slots.get(index);
                int x = panel.cellX(column);
                int y = panel.cellY(row);
                if (!slot.item().isEmpty()) graphics.renderItem(slot.item(), x, y);
                else renderEmptyIcon(graphics, slot.type(), x, y);
                int assignment = selection(slot);
                renderSlotMarker(graphics, x, y, assignment);
                if (event.getMouseX() >= x && event.getMouseX() < x + CELL
                        && event.getMouseY() >= y && event.getMouseY() < y + CELL) hovered = index;
            }
        }
        graphics.flush();
        if (hovered >= 0) {
            Slot slot = slots.get(hovered);
            List<Component> tooltip = new ArrayList<>();
            String key = "curios.identifier." + slot.type();
            String fallback = slot.type().replace('_', ' ');
            String name = I18n.exists(key) ? I18n.get(key)
                    : Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1);
            tooltip.add(Component.literal(name));
            if (!slot.item().isEmpty()) tooltip.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), slot.item()));
            graphics.renderTooltip(Minecraft.getInstance().font, tooltip,
                    java.util.Optional.empty(), event.getMouseX(), event.getMouseY());
            graphics.flush();
        }
    }

    private void renderBackground(GuiGraphics graphics, Layout panel) {
        int x = panel.x();
        int y = panel.y();
        int w = panel.width();
        int h = panel.height();
        // Nine slices keep AE2's one-pixel border intact at any Curios panel size.
        graphics.blit(AE2_BACKGROUND, x, y, 0, 0, 2, 2, 256, 256);
        graphics.blit(AE2_BACKGROUND, x + 2, y, 4, 0, w - 4, 2, 256, 256);
        graphics.blit(AE2_BACKGROUND, x + w - 2, y, 254, 0, 2, 2, 256, 256);
        graphics.blit(AE2_BACKGROUND, x, y + 2, 0, 4, 2, h - 4, 256, 256);
        graphics.blit(AE2_BACKGROUND, x + 2, y + 2, 4, 4, w - 4, h - 4, 256, 256);
        graphics.blit(AE2_BACKGROUND, x + w - 2, y + 2, 254, 4, 2, h - 4, 256, 256);
        graphics.blit(AE2_BACKGROUND, x, y + h - 2, 0, 254, 2, 2, 256, 256);
        graphics.blit(AE2_BACKGROUND, x + 2, y + h - 2, 4, 254, w - 4, 2, 256, 256);
        graphics.blit(AE2_BACKGROUND, x + w - 2, y + h - 2, 254, 254, 2, 2, 256, 256);
    }

    private void renderSlotGrid(GuiGraphics graphics, Layout panel) {
        // The card's first inventory cell begins at (8,79): (7,80) omits its
        // top edge and shifts the 16-pixel item area relative to the slot.
        graphics.blit(CARD_PANEL, panel.cellX(0) - 1, panel.cellY(0) - 1,
                7, 78, panel.columns() * CELL, 1, 256, 256);
        for (int row = 0; row < panel.rows(); row++) {
            int sourceY = row == panel.rows() - 1 ? 115 : 79;
            graphics.blit(CARD_PANEL, panel.cellX(0) - 1, panel.cellY(row),
                    7, sourceY, 1, CELL, 256, 256);
            for (int column = 0; column < panel.columns(); column++) {
                int sourceX = column == panel.columns() - 1 ? 152 : 8;
                graphics.blit(CARD_PANEL, panel.cellX(column), panel.cellY(row),
                        sourceX, sourceY, CELL, CELL, 256, 256);
            }
        }
    }

    private void renderEmptyIcon(GuiGraphics graphics, String type, int x, int y) {
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite sprite = atlas.apply(CuriosApi.getSlotIcon(type));
        if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
            sprite = atlas.apply(EMPTY_SLOT);
        }
        if (!sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
            graphics.blit(x, y, 0, 16, 16, sprite);
        }
    }

    private void renderSlotMarker(GuiGraphics graphics, int x, int y, int assignment) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        if (assignment >= 0) {
            String number = Integer.toString(assignment + 1);
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, number, x + 16 - font.width(number), y, 0xFF00FF00);
        } else {
            graphics.blit(XMARK, x, y, 0, 0, 16, 16, 16, 16);
        }
        graphics.pose().popPose();
    }

    private int selection(Slot slot) {
        return selected.stream().filter(value -> value.type().equals(slot.type()) && value.index() == slot.index())
                .mapToInt(CuriosSelections.Selection::filter).findFirst().orElse(-1);
    }

    @SubscribeEvent
    public void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!CuriosIntegration.enabled()) return;
        try {
            clickPanel(event);
        } catch (LinkageError | RuntimeException error) {
            CuriosIntegration.disable(error);
            open = false;
        }
    }

    private void clickPanel(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!open || event.getScreen() != current) return;
        List<Slot> slots = slots();
        Layout panel = layout(current, slots.size());
        double x = event.getMouseX();
        double y = event.getMouseY();
        if (x < panel.x() || x >= panel.x() + panel.width()
                || y < panel.y() || y >= panel.y() + panel.height()) return;
        event.setCanceled(true);
        for (int column = 0; column < panel.columns(); column++) {
            for (int row = 0; row < panel.rows(); row++) {
                int index = (firstColumn + column) * panel.rows() + row;
                if (index >= slots.size()) break;
                int cellX = panel.cellX(column);
                int cellY = panel.cellY(row);
                if (x < cellX || x >= cellX + CELL || y < cellY || y >= cellY + CELL) continue;
                Slot slot = slots.get(index);
                int next = event.getButton() == 1 ? -1 : (selection(slot) + 1) % 18;
                selected.removeIf(value -> value.type().equals(slot.type()) && value.index() == slot.index());
                if (next >= 0) selected.add(new CuriosSelections.Selection(slot.type(), slot.index(), next));
                CuriosNetwork.send(current.getMenu().containerId, slot.type(), slot.index(), next);
                return;
            }
        }
    }

    @SubscribeEvent
    public void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!CuriosIntegration.enabled() || !open || event.getScreen() != current) return;
        List<Slot> slots = slots();
        Layout panel = layout(current, slots.size());
        if (event.getMouseX() < panel.x() || event.getMouseX() >= panel.x() + panel.width()
                || event.getMouseY() < panel.y() || event.getMouseY() >= panel.y() + panel.height()) return;
        int max = Math.max(0, (slots.size() + panel.rows() - 1) / panel.rows() - panel.columns());
        firstColumn = Math.max(0, Math.min(max, firstColumn - (int) Math.signum(event.getScrollDelta())));
        event.setCanceled(true);
    }
}
