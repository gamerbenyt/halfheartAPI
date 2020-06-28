package me.shedaniel.clothconfig2.forge.gui;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.forge.ClothConfigInitializer;
import me.shedaniel.clothconfig2.forge.api.*;
import me.shedaniel.math.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;

public class GlobalizedClothConfigScreen extends AbstractConfigScreen implements ReferenceBuildingConfigScreen, Expandable {
    public ClothConfigScreen.ListWidget<AbstractConfigEntry<AbstractConfigEntry<?>>> listWidget;
    private Widget cancelButton, exitButton;
    private final LinkedHashMap<ITextComponent, List<AbstractConfigEntry<?>>> categorizedEntries = Maps.newLinkedHashMap();
    private final ScrollingContainer sideScroller = new ScrollingContainer() {
        @Override
        public Rectangle getBounds() {
            return new Rectangle(4, 4, getSideSliderPosition() - 14 - 4, field_230709_l_ - 8);
        }
        
        @Override
        public int getMaxScrollHeight() {
            int i = 0;
            for (Reference reference : references) {
                if (i != 0) i += 3 * reference.getScale();
                i += field_230712_o_.FONT_HEIGHT * reference.getScale();
            }
            return i;
        }
    };
    private Reference lastHoveredReference = null;
    private final ScrollingContainer sideSlider = new ScrollingContainer() {
        private Rectangle empty = new Rectangle();
        
        @Override
        public Rectangle getBounds() {
            return empty;
        }
        
        @Override
        public int getMaxScrollHeight() {
            return 1;
        }
    };
    private final List<Reference> references = Lists.newArrayList();
    private final LazyResettable<Integer> sideExpandLimit = new LazyResettable<>(() -> {
        int max = 0;
        for (Reference reference : references) {
            ITextComponent referenceText = reference.getText();
            int width = field_230712_o_.func_238414_a_(new StringTextComponent(StringUtils.repeat("  ", reference.getIndent()) + "- ").func_230529_a_(referenceText));
            if (width > max) max = width;
        }
        return Math.min(max + 8, field_230708_k_ / 4);
    });
    private boolean requestingReferenceRebuilding = false;
    
    @ApiStatus.Internal
    public GlobalizedClothConfigScreen(Screen parent, ITextComponent title, Map<ITextComponent, List<Object>> entriesMap, ResourceLocation backgroundLocation) {
        super(parent, title, backgroundLocation);
        entriesMap.forEach((categoryName, list) -> {
            List<AbstractConfigEntry<?>> entries = Lists.newArrayList();
            for (Object object : list) {
                AbstractConfigListEntry<?> entry;
                if (object instanceof Tuple<?, ?>) {
                    entry = (AbstractConfigListEntry<?>) ((Tuple<?, ?>) object).getB();
                } else {
                    entry = (AbstractConfigListEntry<?>) object;
                }
                entry.setScreen(this);
                entries.add(entry);
            }
            categorizedEntries.put(categoryName, entries);
        });
        this.sideSlider.scrollTo(0, false);
    }
    
    @Override
    public void requestReferenceRebuilding() {
        this.requestingReferenceRebuilding = true;
    }
    
    @Override
    public Map<ITextComponent, List<AbstractConfigEntry<?>>> getCategorizedEntries() {
        return this.categorizedEntries;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    protected void func_231160_c_() {
        super.func_231160_c_();
        this.sideExpandLimit.reset();
        this.references.clear();
        buildReferences();
        this.field_230705_e_.add(listWidget = new ClothConfigScreen.ListWidget<>(this, field_230706_i_, field_230708_k_ - 14, field_230709_l_, 30, field_230709_l_ - 32, getBackgroundLocation()));
        this.listWidget.setLeftPos(14);
        this.categorizedEntries.forEach((category, entries) -> {
            if (!listWidget.func_231039_at__().isEmpty())
                this.listWidget.func_231039_at__().add((AbstractConfigEntry) new EmptyEntry(5));
            this.listWidget.func_231039_at__().add((AbstractConfigEntry) new EmptyEntry(4));
            this.listWidget.func_231039_at__().add((AbstractConfigEntry) new CategoryTextEntry(category, category.func_230532_e_().func_240699_a_(TextFormatting.BOLD)));
            this.listWidget.func_231039_at__().add((AbstractConfigEntry) new EmptyEntry(4));
            this.listWidget.func_231039_at__().addAll((List) entries);
        });
        int buttonWidths = Math.min(200, (field_230708_k_ - 50 - 12) / 3);
        func_230480_a_(cancelButton = new Button(0, field_230709_l_ - 26, buttonWidths, 20, isEdited() ? new TranslationTextComponent("text.cloth-config.cancel_discard") : new TranslationTextComponent("gui.cancel"), widget -> quit()));
        func_230480_a_(exitButton = new Button(0, field_230709_l_ - 26, buttonWidths, 20, NarratorChatListener.EMPTY, button -> saveAll(true)) {
            @Override
            public void func_230430_a_(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                boolean hasErrors = false;
                label:
                for (List<AbstractConfigEntry<?>> entries : categorizedEntries.values()) {
                    for (AbstractConfigEntry<?> entry : entries) {
                        if (entry.getConfigError().isPresent()) {
                            hasErrors = true;
                            break label;
                        }
                    }
                }
                field_230693_o_ = isEdited() && !hasErrors;
                func_238482_a_(hasErrors ? new TranslationTextComponent("text.cloth-config.error_cannot_save") : new TranslationTextComponent("text.cloth-config.save_and_done"));
                super.func_230430_a_(matrices, mouseX, mouseY, delta);
            }
        });
        Optional.ofNullable(this.afterInitConsumer).ifPresent(consumer -> consumer.accept(this));
    }
    
    private void buildReferences() {
        categorizedEntries.forEach((categoryText, entries) -> {
            this.references.add(new CategoryReference(categoryText));
            for (AbstractConfigEntry<?> entry : entries) buildReferenceFor(entry, 1);
        });
    }
    
    private void buildReferenceFor(AbstractConfigEntry<?> entry, int layer) {
        List<ReferenceProvider<?>> referencableEntries = entry.getReferenceProviderEntries();
        if (referencableEntries != null) {
            this.references.add(new ConfigEntryReference(entry, layer));
            for (ReferenceProvider<?> referencableEntry : referencableEntries) {
                buildReferenceFor(referencableEntry.provideReferenceEntry(), layer + 1);
            }
        }
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public void func_230430_a_(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.lastHoveredReference = null;
        if (requestingReferenceRebuilding) {
            this.references.clear();
            buildReferences();
            requestingReferenceRebuilding = false;
        }
        int sliderPosition = getSideSliderPosition();
        ScissorsHandler.INSTANCE.scissor(new Rectangle(sliderPosition, 0, field_230708_k_ - sliderPosition, field_230709_l_));
        if (isTransparentBackground()) {
            func_238468_a_(matrices, 14, 0, field_230708_k_, field_230709_l_, -1072689136, -804253680);
        } else {
            func_231165_f_(0);
            overlayBackground(matrices, new Rectangle(14, 0, field_230708_k_, field_230709_l_), 64, 64, 64, 255, 255);
        }
        listWidget.width = field_230708_k_ - sliderPosition;
        listWidget.setLeftPos(sliderPosition);
        listWidget.func_230430_a_(matrices, mouseX, mouseY, delta);
        ScissorsHandler.INSTANCE.scissor(new Rectangle(listWidget.left, listWidget.top, listWidget.width, listWidget.bottom - listWidget.top));
        for (AbstractConfigEntry<?> child : listWidget.func_231039_at__())
            child.lateRender(matrices, mouseX, mouseY, delta);
        ScissorsHandler.INSTANCE.removeLastScissor();
        field_230712_o_.func_238407_a_(matrices, field_230704_d_, sliderPosition + (field_230708_k_ - sliderPosition) / 2f - field_230712_o_.func_238414_a_(field_230704_d_) / 2f, 12, -1);
        ScissorsHandler.INSTANCE.removeLastScissor();
        cancelButton.field_230690_l_ = sliderPosition + (field_230708_k_ - sliderPosition) / 2 - cancelButton.func_230998_h_() - 3;
        exitButton.field_230690_l_ = sliderPosition + (field_230708_k_ - sliderPosition) / 2 + 3;
        super.func_230430_a_(matrices, mouseX, mouseY, delta);
        sideSlider.updatePosition(delta);
        sideScroller.updatePosition(delta);
        if (isTransparentBackground()) {
            func_238468_a_(matrices, 0, 0, sliderPosition, field_230709_l_, -1240461296, -972025840);
            func_238468_a_(matrices, 0, 0, sliderPosition - 14, field_230709_l_, 1744830464, 1744830464);
        } else {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            field_230706_i_.getTextureManager().bindTexture(getBackgroundLocation());
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            float f = 32.0F;
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            buffer.pos(sliderPosition - 14, field_230709_l_, 0.0D).tex(0, field_230709_l_ / 32.0F).color(68, 68, 68, 255).endVertex();
            buffer.pos(sliderPosition, field_230709_l_, 0.0D).tex(14 / 32.0F, field_230709_l_ / 32.0F).color(68, 68, 68, 255).endVertex();
            buffer.pos(sliderPosition, 0, 0.0D).tex(14 / 32.0F, 0).color(68, 68, 68, 255).endVertex();
            buffer.pos(sliderPosition - 14, 0, 0.0D).tex(0, 0).color(68, 68, 68, 255).endVertex();
            tessellator.draw();
            
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            buffer.pos(0, field_230709_l_, 0.0D).tex(0, (field_230709_l_ + (int) sideScroller.scrollAmount) / 32.0F).color(32, 32, 32, 255).endVertex();
            buffer.pos(sliderPosition - 14, field_230709_l_, 0.0D).tex((sliderPosition - 14) / 32.0F, (field_230709_l_ + (int) sideScroller.scrollAmount) / 32.0F).color(32, 32, 32, 255).endVertex();
            buffer.pos(sliderPosition - 14, 0, 0.0D).tex((sliderPosition - 14) / 32.0F, ((int) sideScroller.scrollAmount) / 32.0F).color(32, 32, 32, 255).endVertex();
            buffer.pos(0, 0, 0.0D).tex(0, ((int) sideScroller.scrollAmount) / 32.0F).color(32, 32, 32, 255).endVertex();
            tessellator.draw();
        }
        {
            Matrix4f matrix = matrices.getLast().getMatrix();
            RenderSystem.disableTexture();
            RenderSystem.enableBlend();
            RenderSystem.disableAlphaTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.shadeModel(7425);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            int shadeColor = isTransparentBackground() ? 120 : 160;
            buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(matrix, sliderPosition + 4, 0, 100.0F).color(0, 0, 0, 0).endVertex();
            buffer.pos(matrix, sliderPosition, 0, 100.0F).color(0, 0, 0, shadeColor).endVertex();
            buffer.pos(matrix, sliderPosition, field_230709_l_, 100.0F).color(0, 0, 0, shadeColor).endVertex();
            buffer.pos(matrix, sliderPosition + 4, field_230709_l_, 100.0F).color(0, 0, 0, 0).endVertex();
            tessellator.draw();
            shadeColor /= 2;
            buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(matrix, sliderPosition - 14, 0, 100.0F).color(0, 0, 0, shadeColor).endVertex();
            buffer.pos(matrix, sliderPosition - 14 - 4, 0, 100.0F).color(0, 0, 0, 0).endVertex();
            buffer.pos(matrix, sliderPosition - 14 - 4, field_230709_l_, 100.0F).color(0, 0, 0, 0).endVertex();
            buffer.pos(matrix, sliderPosition - 14, field_230709_l_, 100.0F).color(0, 0, 0, shadeColor).endVertex();
            tessellator.draw();
            RenderSystem.shadeModel(7424);
            RenderSystem.disableBlend();
            RenderSystem.enableAlphaTest();
            RenderSystem.enableTexture();
        }
        Rectangle slideArrowBounds = new Rectangle(sliderPosition - 14, 0, 14, field_230709_l_);
        {
            RenderSystem.enableAlphaTest();
            IRenderTypeBuffer.Impl immediate = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());
            field_230712_o_.func_238426_c_(ITextProperties.func_240652_a_(">"), sliderPosition - 7 - field_230712_o_.getStringWidth(">") / 2f, field_230709_l_ / 2, (slideArrowBounds.contains(mouseX, mouseY) ? 16777120 : 16777215) | MathHelper.clamp(MathHelper.ceil((1 - sideSlider.scrollAmount) * 255.0F), 0, 255) << 24, false, matrices.getLast().getMatrix(), immediate, false, 0, 15728880);
            field_230712_o_.func_238426_c_(ITextProperties.func_240652_a_("<"), sliderPosition - 7 - field_230712_o_.getStringWidth("<") / 2f, field_230709_l_ / 2, (slideArrowBounds.contains(mouseX, mouseY) ? 16777120 : 16777215) | MathHelper.clamp(MathHelper.ceil(sideSlider.scrollAmount * 255.0F), 0, 255) << 24, false, matrices.getLast().getMatrix(), immediate, false, 0, 15728880);
            immediate.finish();
            
            Rectangle scrollerBounds = sideScroller.getBounds();
            if (!scrollerBounds.isEmpty()) {
                ScissorsHandler.INSTANCE.scissor(new Rectangle(0, 0, sliderPosition - 14, field_230709_l_));
                int scrollOffset = (int) (scrollerBounds.y - sideScroller.scrollAmount);
                for (Reference reference : references) {
                    matrices.push();
                    matrices.scale(reference.getScale(), reference.getScale(), reference.getScale());
                    IFormattableTextComponent text = new StringTextComponent(StringUtils.repeat("  ", reference.getIndent()) + "- ").func_230529_a_(reference.getText());
                    if (lastHoveredReference == null && new Rectangle(scrollerBounds.x, (int) (scrollOffset - 4 * reference.getScale()), (int) (field_230712_o_.func_238414_a_(text) * reference.getScale()), (int) ((field_230712_o_.FONT_HEIGHT + 4) * reference.getScale())).contains(mouseX, mouseY))
                        lastHoveredReference = reference;
                    field_230712_o_.func_238422_b_(matrices, text, scrollerBounds.x, scrollOffset, lastHoveredReference == reference ? 16769544 : 16777215);
                    matrices.pop();
                    scrollOffset += (field_230712_o_.FONT_HEIGHT + 3) * reference.getScale();
                }
                ScissorsHandler.INSTANCE.removeLastScissor();
                sideScroller.renderScrollBar();
            }
        }
    }
    
    @Override
    public boolean func_231044_a_(double mouseX, double mouseY, int button) {
        Rectangle slideBounds = new Rectangle(0, 0, getSideSliderPosition() - 14, field_230709_l_);
        if (button == 0 && slideBounds.contains(mouseX, mouseY) && lastHoveredReference != null) {
            field_230706_i_.getSoundHandler().play(SimpleSound.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            lastHoveredReference.go();
            return true;
        }
        Rectangle slideArrowBounds = new Rectangle(getSideSliderPosition() - 14, 0, 14, field_230709_l_);
        if (button == 0 && slideArrowBounds.contains(mouseX, mouseY)) {
            setExpanded(!isExpanded());
            field_230706_i_.getSoundHandler().play(SimpleSound.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.func_231044_a_(mouseX, mouseY, button);
    }
    
    @Override
    public boolean isExpanded() {
        return sideSlider.scrollTarget == 1;
    }
    
    @Override
    public void setExpanded(boolean expanded) {
        this.sideSlider.scrollTo(expanded ? 1 : 0, true, 2000);
    }
    
    @Override
    public boolean func_231043_a_(double mouseX, double mouseY, double amount) {
        Rectangle slideBounds = new Rectangle(0, 0, getSideSliderPosition() - 14, field_230709_l_);
        if (slideBounds.contains(mouseX, mouseY)) {
            sideScroller.offset(ClothConfigInitializer.getScrollStep() * -amount, true);
            return true;
        }
        return super.func_231043_a_(mouseX, mouseY, amount);
    }
    
    private int getSideSliderPosition() {
        return (int) (sideSlider.scrollAmount * sideExpandLimit.get() + 14);
    }
    
    private static class EmptyEntry extends AbstractConfigListEntry<Object> {
        private final int height;
        
        public EmptyEntry(int height) {
            super(new StringTextComponent(UUID.randomUUID().toString()), false);
            this.height = height;
        }
        
        @Override
        public int getItemHeight() {
            return height;
        }
        
        @Override
        public Object getValue() {
            return null;
        }
        
        @Override
        public Optional<Object> getDefaultValue() {
            return Optional.empty();
        }
        
        @Override
        public void save() {}
        
        @Override
        public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {}
        
        @Override
        public List<? extends IGuiEventListener> func_231039_at__() {
            return Collections.emptyList();
        }
    }
    
    private static class CategoryTextEntry extends AbstractConfigListEntry<Object> {
        private final ITextComponent category;
        private final ITextComponent text;
        
        public CategoryTextEntry(ITextComponent category, ITextComponent text) {
            super(new StringTextComponent(UUID.randomUUID().toString()), false);
            this.category = category;
            this.text = text;
        }
        
        @Override
        public int getItemHeight() {
            List<ITextProperties> strings = Minecraft.getInstance().fontRenderer.func_238425_b_(text, getParent().getItemWidth());
            if (strings.isEmpty())
                return 0;
            return 4 + strings.size() * 10;
        }
        
        @Override
        public Object getValue() {
            return null;
        }
        
        @Override
        public Optional<Object> getDefaultValue() {
            return Optional.empty();
        }
        
        @Override
        public void save() {}
        
        @Override
        public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
            int yy = y + 2;
            List<ITextProperties> texts = Minecraft.getInstance().fontRenderer.func_238425_b_(this.text, getParent().getItemWidth());
            for (ITextProperties text : texts) {
                Minecraft.getInstance().fontRenderer.func_238407_a_(matrices, text, x - 4 + entryWidth / 2 - Minecraft.getInstance().fontRenderer.func_238414_a_(text) / 2, yy, -1);
                yy += 10;
            }
        }
        
        @Override
        public List<? extends IGuiEventListener> func_231039_at__() {
            return Collections.emptyList();
        }
    }
    
    private interface Reference {
        default int getIndent() {
            return 0;
        }
        
        ITextComponent getText();
        
        float getScale();
        
        void go();
    }
    
    private class CategoryReference implements Reference {
        private ITextComponent category;
        
        public CategoryReference(ITextComponent category) {
            this.category = category;
        }
        
        @Override
        public ITextComponent getText() {
            return category;
        }
        
        @Override
        public float getScale() {
            return 1.0F;
        }
        
        @Override
        public void go() {
            int i = 0;
            for (AbstractConfigEntry<?> child : listWidget.func_231039_at__()) {
                if (child instanceof CategoryTextEntry && ((CategoryTextEntry) child).category == category) {
                    listWidget.scrollTo(i, true);
                    return;
                }
                i += child.getItemHeight();
            }
        }
    }
    
    private class ConfigEntryReference implements Reference {
        private AbstractConfigEntry<?> entry;
        private int layer;
        
        public ConfigEntryReference(AbstractConfigEntry<?> entry, int layer) {
            this.entry = entry;
            this.layer = layer;
        }
        
        @Override
        public int getIndent() {
            return layer;
        }
        
        @Override
        public ITextComponent getText() {
            return entry.getFieldName();
        }
        
        @Override
        public float getScale() {
            return 1.0F;
        }
        
        @Override
        public void go() {
            int[] i = {0};
            for (AbstractConfigEntry<?> child : listWidget.func_231039_at__()) {
                int i1 = i[0];
                if (goChild(i, null, child)) return;
                i[0] = i1 + child.getItemHeight();
            }
        }
        
        private boolean goChild(int[] i, Integer expandedParent, AbstractConfigEntry<?> root) {
            if (root == entry) {
                listWidget.scrollTo(expandedParent == null ? i[0] : expandedParent, true);
                return true;
            }
            int j = i[0];
            i[0] += root.getInitialReferenceOffset();
            boolean expanded = root instanceof Expandable && ((Expandable) root).isExpanded();
            if (root instanceof Expandable) ((Expandable) root).setExpanded(true);
            List<? extends IGuiEventListener> children = root.func_231039_at__();
            if (root instanceof Expandable) ((Expandable) root).setExpanded(expanded);
            for (IGuiEventListener child : children) {
                if (child instanceof ReferenceProvider<?>) {
                    int i1 = i[0];
                    if (goChild(i, expandedParent != null ? expandedParent : root instanceof Expandable && !expanded ? j : null, ((ReferenceProvider<?>) child).provideReferenceEntry())) {
                        return true;
                    }
                    i[0] = i1 + ((ReferenceProvider<?>) child).provideReferenceEntry().getItemHeight();
                }
            }
            return false;
        }
    }
}