package com.tom.storagemod.util;

import net.minecraft.nbt.NbtCompound;

public class NBTUtil {
    public static boolean areNbtEqual(net.minecraft.item.ItemStack left, net.minecraft.item.ItemStack right) {
        NbtCompound leftNbt = null;
        NbtCompound rightNbt = null;
        if (left.getNbt() == null) {
            leftNbt = left.getOrCreateNbt();
        } else {
            leftNbt = left.getNbt();
        }
        if (right.getNbt() == null) {
            rightNbt = right.getOrCreateNbt();
        } else {
            rightNbt = right.getNbt();
        }
        return leftNbt.equals(rightNbt);
    }
}
