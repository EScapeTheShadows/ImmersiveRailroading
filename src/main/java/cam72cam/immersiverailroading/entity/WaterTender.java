package cam72cam.immersiverailroading.entity;

import java.util.List;

import cam72cam.immersiverailroading.inventory.SlotFilter;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.registry.WaterTenderDefinition;
import cam72cam.immersiverailroading.util.LiquidUtil;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

public class WaterTender extends CarTank {
	
	public WaterTender(World world) {
		this(world, null);
	}

	public WaterTender(World world, String defID) {
		super(world, defID);
	}
	
	@Override
	public WaterTenderDefinition getDefinition() {
		return super.getDefinition(WaterTenderDefinition.class);
	}
	
	@Override
	public GuiTypes guiType() {
		return GuiTypes.TANK;
	}

	@Override
	public List<Fluid> getFluidFilter() {
		return LiquidUtil.getWater();
	}
	
	@Override
	protected void initContainerFilter() {
		cargoItems.filter.clear();
		cargoItems.filter.put(getInventorySize()-2, SlotFilter.FLUID_CONTAINER);
		cargoItems.filter.put(getInventorySize()-1, SlotFilter.FLUID_CONTAINER);
		cargoItems.defaultFilter = SlotFilter.BURNABLE;
	}
	
	@Override
	protected int[] getContainerInputSlots() {
		return new int[] { getInventorySize()-2 };
	}
	@Override
	protected int[] getContainertOutputSlots() {
		return new int[] { getInventorySize()-1 };
	}
}