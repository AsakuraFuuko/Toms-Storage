package com.tom.storagemod;

import net.fabricmc.fabric.api.tag.TagFactory;
import net.minecraft.block.Block;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;

public class StorageTags {
	public static final Tag<Block> REMOTE_ACTIVATE = TagFactory.BLOCK.create(new Identifier("toms_storage", "remote_activate"));

	public static void init() {}
}
