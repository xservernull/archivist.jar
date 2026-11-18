// registers every ported block, its item, the reactor block entity and the creative tab
// never rename the ids, dimensions and saves use them
package com.xservernull.archivist.block;

import com.xservernull.archivist.Texts;
import com.xservernull.archivist.Archivist;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModBlocks {
    private ModBlocks() {}

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Archivist.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Archivist.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Archivist.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Archivist.MOD_ID);

    private static final List<Supplier<Item>> TAB_ITEMS = new ArrayList<>();

    public static final Supplier<Block> GRASS_4KB = copyOf("grass_4kb", () -> Blocks.GRASS_BLOCK);
    public static final Supplier<Block> DIRT_4KB = copyOf("dirt_4kb", () -> Blocks.DIRT);
    public static final Supplier<Block> STONE_4KB = copyOf("stone_4kb", () -> Blocks.STONE);
    public static final Supplier<Block> BRICKS_4KB = copyOf("bricks_4kb", () -> Blocks.BRICKS);
    public static final Supplier<Block> LOG_4KB = block("log_4kb", () -> new RotatedPillarBlock(Properties.ofFullCopy(Blocks.OAK_LOG)));
    public static final Supplier<Block> LEAVES_4KB = copyOf("leaves_4kb", () -> Blocks.OAK_LEAVES);

    public static final Supplier<Block> XOR_BLOCK = block("xor_block",
            () -> new Block(Properties.ofFullCopy(Blocks.OBSIDIAN).lightLevel(state -> 15)));

    public static final Supplier<Block> AETHER_GRASS = copyOf("aether_grass", () -> Blocks.GRASS_BLOCK);
    public static final Supplier<Block> CLOUD_BLOCK = block("cloud_block", CloudBlock::new,
            Texts.tr("archivist.tooltip.cloud_1", "Soft and fluffy").withStyle(ChatFormatting.AQUA),
            Texts.tr("archivist.tooltip.cloud_2", "Slightly slows your movement").withStyle(ChatFormatting.GRAY));
    public static final Supplier<Block> AETHERPORTALSTONE = block("aetherportalstone",
            () -> new Block(Properties.of().mapColor(MapColor.GOLD).strength(0.3F).sound(SoundType.GLASS)
                    .lightLevel(state -> 15).noOcclusion()),
            Component.literal("fromaplacefaraway"));

    public static final Supplier<Block> DOGTOOTH = block("dogtooth",
            () -> new AmethystBlock(Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5F).sound(SoundType.AMETHYST)),
            Component.literal("dogtooth amethyst"));

    public static final Supplier<Block> FAKE_GRASS = block("fake_grass", FakeGrassBlock::new,
            Component.literal("renderedincomplete"));
    public static final Supplier<Block> FAKE_BEDROCK = block("fake_bedrock",
            () -> new Block(Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE)),
            Component.literal("fromaplacefaraway"));
    public static final Supplier<Block> STATIC_LAVA_BLOCK = block("static_lava_block", StaticLavaBlock::new);

    public static final Supplier<Block> OLD_NETHERRACK = copyOf("old_netherrack", () -> Blocks.NETHERRACK);
    public static final Supplier<Block> OLD_CRIMSON_NYLIUM = copyOf("old_crimson_nylium", () -> Blocks.CRIMSON_NYLIUM);
    public static final Supplier<Block> OLD_NETHER_GOLD_ORE = copyOf("old_nether_gold_ore", () -> Blocks.NETHER_GOLD_ORE);
    public static final Supplier<Block> OLD_NETHER_QUARTZ_ORE = copyOf("old_nether_quartz_ore", () -> Blocks.NETHER_QUARTZ_ORE);
    public static final Supplier<Block> REACTOR_CORE = block("nether_reactor_core", ReactorCoreBlock::new,
            Component.literal("netherreactorcore"));
    public static final Supplier<Block> REACTOR_EXIT = BLOCKS.register("reactor_exit", ReactorExitBlock::new);
    public static final Supplier<BlockEntityType<ReactorCoreEntity>> REACTOR_CORE_ENTITY = BLOCK_ENTITIES.register("nether_reactor_core",
            () -> BlockEntityType.Builder.of(ReactorCoreEntity::new, REACTOR_CORE.get()).build(null));

    static {
        TABS.register("archivist_tab", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.archivist.archivist_tab"))
                .icon(() -> new ItemStack(Blocks.OBSIDIAN))
                .displayItems((parameters, output) -> TAB_ITEMS.forEach(item -> output.accept(item.get())))
                .build());
    }

    private static Supplier<Block> block(String id, Supplier<Block> factory, Component... lore) {
        Supplier<Block> block = BLOCKS.register(id, factory);
        TAB_ITEMS.add(ITEMS.register(id, () -> new LoreBlockItem(block.get(), lore)));
        return block;
    }

    private static Supplier<Block> copyOf(String id, Supplier<Block> vanilla) {
        return block(id, () -> new Block(Properties.ofFullCopy(vanilla.get())));
    }

    public static void addToTab(Supplier<? extends Item> item) {
        TAB_ITEMS.add(item::get);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}