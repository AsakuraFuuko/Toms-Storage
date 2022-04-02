package com.tom.storagemod.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.InventoryProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import com.tom.storagemod.Config;
import com.tom.storagemod.StorageMod;
import com.tom.storagemod.TickerUtil.TickableServer;
import com.tom.storagemod.block.ITrim;
import com.tom.storagemod.util.IProxy;
import com.tom.storagemod.util.InfoHandler;
import com.tom.storagemod.util.InventoryWrapper;

public class TileEntityInventoryConnector extends BlockEntity implements TickableServer, Inventory {
	private List<InventoryWrapper> handlers = new ArrayList<>();
	private List<LinkedInv> linkedInvs = new ArrayList<>();
	private int[] invSizes = new int[0];
	private int invSize;

	public TileEntityInventoryConnector(BlockPos pos, BlockState state) {
		super(StorageMod.connectorTile, pos, state);
	}

	@Override
	public void updateServer() {
		long time = world.getTime();
		if(time % 20 == 0) {
			CablePathCache.Clear();
			Stack<BlockPos> toCheck = new Stack<>();
			Set<BlockPos> checkedBlocks = new HashSet<>();
			toCheck.add(pos);
			checkedBlocks.add(pos);
			handlers.clear();
			Set<LinkedInv> toRM = new HashSet<>();
			for (LinkedInv inv : linkedInvs) {
				if(inv.time + 40 < time) {
					toRM.add(inv);
					continue;
				}
				InventoryWrapper w = inv.handler.get();
				if(w != null) {
					Inventory ihr = IProxy.resolve(w.getInventory());
					if(ihr instanceof TileEntityInventoryConnector) {
						TileEntityInventoryConnector ih = (TileEntityInventoryConnector) ihr;
						if(checkHandlers(ih, 0)) {
							if(!handlers.contains(InfoHandler.INSTANCE))handlers.add(InfoHandler.INSTANCE);
							continue;
						}
					}
					handlers.add(w);
				}
			}
			linkedInvs.removeAll(toRM);
			Collections.sort(linkedInvs);
			int range = StorageMod.CONFIG.invRange * StorageMod.CONFIG.invRange;
			while(!toCheck.isEmpty()) {
				BlockPos cp = toCheck.pop();
				for (Direction d : Direction.values()) {
					BlockPos p = cp.offset(d);
					if(!checkedBlocks.contains(p) && p.getSquaredDistance(pos) < range) {
						checkedBlocks.add(p);
						BlockState state = world.getBlockState(p);
						if(state.getBlock() instanceof ITrim) {
							toCheck.add(p);
						} else {
							BlockEntity te = world.getBlockEntity(p);
							if (te instanceof TileEntityInventoryConnector || te instanceof TileEntityInventoryProxy || te instanceof TileEntityInventoryCableConnectorBase) {
								continue;
							} else if(te != null && !StorageMod.CONFIG.onlyTrims) {
								Inventory ihr = null;
								Inventory inv = getInventoryAt(world, p);
								if(te instanceof ChestBlockEntity) {//Check for double chests
									Block block = state.getBlock();
									if(block instanceof ChestBlock) {
										ihr = ChestBlock.getInventory((ChestBlock)block, state, world, p, true);
										ChestType type = state.get(ChestBlock.CHEST_TYPE);
										if (type != ChestType.SINGLE) {
											BlockPos opos = p.offset(ChestBlock.getFacing(state));
											BlockState ostate = this.getWorld().getBlockState(opos);
											if (state.getBlock() == ostate.getBlock()) {
												ChestType otype = ostate.get(ChestBlock.CHEST_TYPE);
												if (otype != ChestType.SINGLE && type != otype && state.get(ChestBlock.FACING) == ostate.get(ChestBlock.FACING)) {
													toCheck.add(opos);
													checkedBlocks.add(opos);
												}
											}
										}
									}
								}
								if(inv != null) {
									ihr = IProxy.resolve(inv);
									if(ihr instanceof TileEntityInventoryConnector) {
										TileEntityInventoryConnector ih = (TileEntityInventoryConnector) ihr;
										if(checkHandlers(ih, 0)) {
											if(!handlers.contains(InfoHandler.INSTANCE))handlers.add(InfoHandler.INSTANCE);
											continue;
										}
									}
									if(!(te instanceof TileEntityInventoryCableConnectorBase))
										toCheck.add(p);
								}
								if(ihr != null)handlers.add(new InventoryWrapper(ihr, d.getOpposite()));

								if(Config.getMultiblockInvs().contains(state.getBlock())) {
									skipBlocks(p, checkedBlocks, toCheck, state.getBlock());
								}
							}
						}
					}
				}
			}
			if(invSizes.length != handlers.size())invSizes = new int[handlers.size()];
			invSize = 0;
			for (int i = 0; i < invSizes.length; i++) {
				InventoryWrapper ih = handlers.get(i);
				if(ih == null)invSizes[i] = 0;
				else {
					int s = ih.size();
					invSizes[i] = s;
					invSize += s;
				}
			}
		}
	}

	private void skipBlocks(BlockPos pos, Set<BlockPos> checkedBlocks, Stack<BlockPos> edges, Block block) {
		Stack<BlockPos> toCheck = new Stack<>();
		toCheck.add(pos);
		edges.add(pos);
		while(!toCheck.isEmpty()) {
			BlockPos cp = toCheck.pop();
			for (Direction d : Direction.values()) {
				BlockPos p = cp.offset(d);
				if(!checkedBlocks.contains(p) && p.getSquaredDistance(pos) < StorageMod.CONFIG.invRange) {
					BlockState state = world.getBlockState(p);
					if(state.getBlock() == block) {
						checkedBlocks.add(p);
						edges.add(p);
						toCheck.add(p);
					}
				}
			}
		}
	}

	private boolean checkHandlers(TileEntityInventoryConnector ih, int depth) {
		if(depth > 3)return true;
		for (InventoryWrapper lo : ih.handlers) {
			Inventory ihr = IProxy.resolve(lo.getInventory());
			if(ihr == this)return true;
			if(ihr instanceof TileEntityInventoryConnector) {
				if(checkHandlers((TileEntityInventoryConnector) ihr, depth+1))return true;
			}
		}
		return false;
	}

	private boolean calling;
	public <R> R call(BiFunction<InventoryWrapper, Integer, R> func, int slot, R def) {
		if(calling)return def;
		calling = true;
		for (int i = 0; i < invSizes.length; i++) {
			if(slot >= invSizes[i])slot -= invSizes[i];
			else {
				R r = func.apply(handlers.get(i), slot);
				calling = false;
				return r;
			}
		}
		calling = false;
		return def;
	}

	@Override
	public void clear() {
	}

	@Override
	public int size() {
		return invSize;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public ItemStack getStack(int paramInt) {
		return call(InventoryWrapper::getStack, paramInt, ItemStack.EMPTY);
	}

	@Override
	public ItemStack removeStack(int paramInt1, int paramInt2) {
		return call((i, s) -> i.removeStack(s, paramInt2), paramInt1, ItemStack.EMPTY);
	}

	@Override
	public ItemStack removeStack(int paramInt) {
		return call(InventoryWrapper::removeStack, paramInt, ItemStack.EMPTY);
	}

	@Override
	public void setStack(int paramInt, ItemStack paramItemStack) {
		call((i, s) -> {
			i.setStack(s, paramItemStack);
			return Unit.INSTANCE;
		}, paramInt, Unit.INSTANCE);
	}

	@Override
	public boolean canPlayerUse(PlayerEntity paramPlayerEntity) {
		return false;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return call((i, s) -> i.isValid(s, stack, false), slot, false);
	}

	public void addLinked(LinkedInv inv) {
		linkedInvs.add(inv);
	}

	public static class LinkedInv implements Comparable<LinkedInv> {
		public Supplier<InventoryWrapper> handler;
		public long time;
		public int priority;

		@Override
		public int compareTo(LinkedInv o) {
			return Integer.compare(priority, o.priority);
		}
	}

	public void unLink(LinkedInv linv) {
		linkedInvs.remove(linv);
	}

	public InventoryWrapper getInventory() {
		return new InventoryWrapper(this, Direction.DOWN);
	}

	public int getFreeSlotCount() {
		int empty = 0;
		for(int i = 0;i<invSize;i++) {
			if(getStack(i).isEmpty())empty++;
		}
		return empty;
	}

	public static Inventory getInventoryAt(World world, BlockPos blockPos) {
		Inventory inventory = null;
		BlockState blockState = world.getBlockState(blockPos);
		Block block = blockState.getBlock();
		if (block instanceof InventoryProvider) {
			inventory = ((InventoryProvider) block).getInventory(blockState, world, blockPos);
		} else if (blockState.hasBlockEntity()) {
			BlockEntity blockEntity = world.getBlockEntity(blockPos);
			if (blockEntity instanceof Inventory) {
				inventory = (Inventory) blockEntity;
				if (inventory instanceof ChestBlockEntity && block instanceof ChestBlock) {
					inventory = ChestBlock.getInventory((ChestBlock) block, blockState, world, blockPos, true);
				}
			}
		}

		return inventory;
	}
}
