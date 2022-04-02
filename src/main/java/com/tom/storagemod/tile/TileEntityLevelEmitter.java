package com.tom.storagemod.tile;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import com.tom.storagemod.StorageMod;
import com.tom.storagemod.TickerUtil.TickableServer;
import com.tom.storagemod.block.BlockInventoryCableConnector;
import com.tom.storagemod.block.BlockLevelEmitter;
import com.tom.storagemod.block.IInventoryCable;
import com.tom.storagemod.gui.ContainerLevelEmitter;
import com.tom.storagemod.util.EmptyHandler;
import com.tom.storagemod.util.IItemHandler;
import com.tom.storagemod.util.InventoryWrapper;

public class TileEntityLevelEmitter extends BlockEntity implements TickableServer, NamedScreenHandlerFactory {
	private ItemStack filter = ItemStack.EMPTY;
	private int count;
	private InventoryWrapper top;
	private boolean lessThan;

	public TileEntityLevelEmitter(BlockPos pos, BlockState state) {
		super(StorageMod.levelEmitterTile, pos, state);
	}

	@Override
	public void updateServer() {
		if (world.getTime() % 20 == 1) {
			Stack<BlockPos> inventoryCableStack = new Stack<>();
			BlockState state = world.getBlockState(pos);
			Direction facing = state.get(BlockInventoryCableConnector.FACING);
			Stack<BlockPos> toCheck = new Stack<>();
			Set<BlockPos> checkedBlocks = new HashSet<>();
			checkedBlocks.add(pos);
			BlockPos up = pos.offset(facing.getOpposite());
			state = world.getBlockState(up);
			if (state.getBlock() instanceof IInventoryCable) {
				top = null;
				toCheck.add(up);
				while (!toCheck.isEmpty()) {
					BlockPos cp = toCheck.pop();
					// Try find connector from cache
					BlockPos connectorBlockPos = CablePathCache.tryGet(cp);
					if (connectorBlockPos != null) {
						BlockEntity te = world.getBlockEntity(connectorBlockPos);
						if (te instanceof TileEntityInventoryConnector) {
							top = ((TileEntityInventoryConnector) te).getInventory();
							toCheck.clear();
							break;
						}
					}
					if (!checkedBlocks.contains(cp)) {
						checkedBlocks.add(cp);
						if (world.canSetBlock(cp)) {
							state = world.getBlockState(cp);
							if (state.getBlock() == StorageMod.connector) {
								BlockEntity te = world.getBlockEntity(cp);
								if (te instanceof TileEntityInventoryConnector) {
									top = ((TileEntityInventoryConnector) te).getInventory();
									for (BlockPos pos : inventoryCableStack) {
										CablePathCache.Put(pos, cp);
									}
								}
								break;
							}
							if (state.getBlock() instanceof IInventoryCable) {
								inventoryCableStack.add(cp);
								toCheck.addAll(((IInventoryCable) state.getBlock()).next(world, state, cp));
							}
						}
						if (checkedBlocks.size() > StorageMod.CONFIG.invConnectorMaxCables)
							break;
					}
				}
			}
		}
		if (world.getTime() % 10 == 2 && top != null) {
			BlockState state = world.getBlockState(pos);
			boolean p = state.get(BlockLevelEmitter.POWERED);
			boolean currState = false;
			IItemHandler top = this.top == null ? EmptyHandler.INSTANCE : this.top.wrap();
			if (!filter.isEmpty()) {
				int counter = 0;
				for (int i = 0; i < top.getSlots(); i++) {
					ItemStack inSlot = top.getStackInSlot(i);
					if (!ItemStack.areItemsEqual(inSlot, getFilter()) || !ItemStack.areNbtEqual(inSlot, getFilter())) {
						continue;
					}
					counter += inSlot.getCount();
				}
				if (lessThan) {
					currState = counter < count;
				} else {
					currState = counter > count;
				}
			} else {
				currState = false;
			}
			if (currState != p) {
				world.setBlockState(pos, state.with(BlockLevelEmitter.POWERED, Boolean.valueOf(currState)), 3);

				Direction direction = state.get(BlockLevelEmitter.FACING);
				BlockPos blockPos = pos.offset(direction.getOpposite());

				world.updateNeighbor(blockPos, state.getBlock(), pos);
				world.updateNeighborsExcept(blockPos, state.getBlock(), direction);
			}
		}
	}

	@Override
	public void writeNbt(NbtCompound compound) {
		compound.put("Filter", getFilter().writeNbt(new NbtCompound()));
		compound.putInt("Count", count);
		compound.putBoolean("lessThan", lessThan);
	}

	@Override
	public void readNbt(NbtCompound nbtIn) {
		super.readNbt(nbtIn);
		setFilter(ItemStack.fromNbt(nbtIn.getCompound("Filter")));
		count = nbtIn.getInt("Count");
		lessThan = nbtIn.getBoolean("lessThan");
	}

	public void setFilter(ItemStack filter) {
		this.filter = filter;
	}

	public ItemStack getFilter() {
		return filter;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public int getCount() {
		return count;
	}

	public void setLessThan(boolean lessThan) {
		this.lessThan = lessThan;
	}

	public boolean isLessThan() {
		return lessThan;
	}

	@Override
	public ScreenHandler createMenu(int p_createMenu_1_, PlayerInventory p_createMenu_2_,
			PlayerEntity p_createMenu_3_) {
		return new ContainerLevelEmitter(p_createMenu_1_, p_createMenu_2_, this);
	}

	@Override
	public Text getDisplayName() {
		return new TranslatableText("ts.level_emitter");
	}

	public boolean stillValid(PlayerEntity p_59619_) {
		if (this.world.getBlockEntity(this.pos) != this) {
			return false;
		} else {
			return !(p_59619_.squaredDistanceTo(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D,
					this.pos.getZ() + 0.5D) > 64.0D);
		}
	}
}
