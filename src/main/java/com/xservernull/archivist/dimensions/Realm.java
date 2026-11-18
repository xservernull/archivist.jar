package com.xservernull.archivist.dimensions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.dimensions.Terrains.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * the one chunk generator every archivist dimension uses
 */
public final class Realm extends ChunkGenerator {
    private static final Codec<Kind> KIND_CODEC = Codec.STRING.comapFlatMap(
            id -> {
                Kind kind = Kind.of(id);
                return kind != null
                        ? DataResult.success(kind)
                        : DataResult.<Kind>error(() -> "unknown realm kind '" + id + "', expected one of: " + Kind.names());
            },
            kind -> kind.id);
    public static final MapCodec<Realm> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(Realm::getBiomeSource),
            KIND_CODEC.fieldOf("kind").forGetter(realm -> realm.kind)
    ).apply(instance, Realm::new));
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Archivist.MOD_ID);
    public static void register(IEventBus modBus) {
        GENERATORS.register("realm", () -> CODEC);
        GENERATORS.register(modBus);
    }
    private final Kind kind;
    public Realm(BiomeSource biomeSource, Kind kind) {
        super(biomeSource);
        this.kind = kind;
    }
    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        kind.terrain().generate(chunk);
        return CompletableFuture.completedFuture(chunk);
    }
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        boolean oceanFloor = type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG;
        return kind.terrain().firstFree(x, z, oceanFloor, level.getMinBuildHeight(), level.getMaxBuildHeight());
    }
    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int minY = level.getMinBuildHeight();
        return new NoiseColumn(minY, kind.terrain().column(x, z, minY, level.getMaxBuildHeight()));
    }
    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }
    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
    }
    @Override
    public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager structures,
                                 ChunkAccess chunk, StructureTemplateManager templates) {
    }
    @Override
    public int getGenDepth() {
        return kind.height;
    }
    @Override
    public int getSeaLevel() {
        return kind.seaLevel;
    }
    @Override
    public int getMinY() {
        return kind.minY;
    }
    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Realm: " + kind.id);
    }
}
