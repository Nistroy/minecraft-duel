package io.github.nistroy.duel.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Pose l'arène par colonnes de {@value #SLICE}×{@value #SLICE} blocs, une par tick : la structure
 * (~1,5 M blocs) posée d'un coup gèlerait le serveur au point de déclencher le watchdog.
 */
public final class ArenaBuilder {
	private static final int SLICE = 32;

	private final ServerLevel level;
	private final StructureTemplate template;
	private final BlockPos origin;
	private final Vec3i size;
	private final int slicesX;
	private final int slicesZ;
	private int next;

	public ArenaBuilder(ServerLevel level, StructureTemplate template, BlockPos origin) {
		this.level = level;
		this.template = template;
		this.origin = origin;
		this.size = template.getSize();
		this.slicesX = Math.ceilDiv(size.getX(), SLICE);
		this.slicesZ = Math.ceilDiv(size.getZ(), SLICE);
	}

	public int total() {
		return slicesX * slicesZ;
	}

	public int done() {
		return next;
	}

	/** @return vrai quand toute l'arène est posée. */
	public boolean step() {
		if (next < total()) {
			int x0 = origin.getX() + (next % slicesX) * SLICE;
			int z0 = origin.getZ() + (next / slicesX) * SLICE;
			BoundingBox slice = new BoundingBox(
					x0, origin.getY(), z0,
					x0 + SLICE - 1, origin.getY() + size.getY() - 1, z0 + SLICE - 1);
			StructurePlaceSettings settings = new StructurePlaceSettings().setBoundingBox(slice);
			template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
			next++;
		}
		return next >= total();
	}
}
