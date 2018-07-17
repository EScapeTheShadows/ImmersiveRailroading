package cam72cam.immersiverailroading.entity;

import java.util.List;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.library.RenderComponentType;
import cam72cam.immersiverailroading.model.RenderComponent;
import cam72cam.immersiverailroading.registry.LocomotiveDieselDefinition;
import cam72cam.immersiverailroading.sound.ISound;
import cam72cam.immersiverailroading.util.BurnUtil;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.immersiverailroading.util.VecUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

public class LocomotiveDiesel extends Locomotive {

	private ISound horn;
	private ISound idle;
	private float soundThrottle;
	private float internalBurn = 0;

	public LocomotiveDiesel(World world) {
		this(world, null);
	}

	public LocomotiveDiesel(World world, String defID) {
		super(world, defID);
	}

	@Override
	public LocomotiveDieselDefinition getDefinition() {
		return super.getDefinition(LocomotiveDieselDefinition.class);
	}
	
	@Override
	public GuiTypes guiType() {
		return GuiTypes.DIESEL_LOCOMOTIVE;
	}
	
	
	/*
	 * Sets the throttle or brake on all connected diesel locomotives if the throttle or brake has been changed
	 */
	@Override
	public void handleKeyPress(Entity source, KeyTypes key) {
		super.handleKeyPress(source, key);
		
		this.mapTrain(this, true, false, this::setThrottleMap);
	}
	
	private void setThrottleMap(EntityRollingStock stock, boolean direction) {
		if (stock instanceof LocomotiveDiesel) {
			((LocomotiveDiesel) stock).setThrottle(this.getThrottle() * (direction ? 1 : -1));
			((LocomotiveDiesel) stock).setAirBrake(this.getAirBrake());
		}
	}
	
	@Override
	protected int getAvailableHP() {
		if (!Config.isFuelRequired(gauge)) {
			return this.getDefinition().getHorsePower(gauge);
		}
		return this.getLiquidAmount() > 0 ? this.getDefinition().getHorsePower(gauge) : 0;
	}

	@Override
	public void onUpdate() {
		super.onUpdate();
		
		if (world.isRemote) {
			
			boolean hasFuel = (this.getLiquidAmount() > 0 || !Config.isFuelRequired(gauge));
			
			if (ConfigSound.soundEnabled) {
				if (this.horn == null) {
					this.horn = ImmersiveRailroading.proxy.newSound(this.getDefinition().horn, false, 100, gauge);
					this.idle = ImmersiveRailroading.proxy.newSound(this.getDefinition().idle, true, 80, gauge);
				}
				
				if (hasFuel) {
					if (!idle.isPlaying()) {
						this.idle.play(getPositionVector());
					}
				} else {
					if (idle.isPlaying()) {
						idle.stop();
					}
				}
				
				if (this.getDataManager().get(HORN) != 0 && !horn.isPlaying()) {
					horn.play(getPositionVector());
				}
				
				float absThrottle = Math.abs(this.getThrottle());
				if (this.soundThrottle > absThrottle) {
					this.soundThrottle -= Math.min(0.01f, this.soundThrottle - absThrottle); 
				} else if (this.soundThrottle < Math.abs(this.getThrottle())) {
					this.soundThrottle += Math.min(0.01f, absThrottle - this.soundThrottle);
				}
	
				if (horn.isPlaying()) {
					horn.setPosition(getPositionVector());
					horn.setVelocity(getVelocity());
					horn.update();
				}
				
				if (idle.isPlaying()) {
					idle.setPitch(0.7f+this.soundThrottle/4);
					idle.setVolume(Math.max(0.1f, this.soundThrottle));
					idle.setPosition(getPositionVector());
					idle.setVelocity(getVelocity());
					idle.update();
				}
			}
			
			
			if (!ConfigGraphics.particlesEnabled) {
				return;
			}
			
			Vec3d fakeMotion = new Vec3d(this.motionX, this.motionY, this.motionZ);//VecUtil.fromYaw(this.getCurrentSpeed().minecraft(), this.rotationYaw);
			
			List<RenderComponent> exhausts = this.getDefinition().getComponents(RenderComponentType.DIESEL_EXHAUST_X, gauge);
			float throttle = Math.abs(this.getThrottle());
			if (exhausts != null && throttle > 0 && hasFuel) {
				for (RenderComponent exhaust : exhausts) {
					Vec3d particlePos = this.getPositionVector().add(VecUtil.rotateYaw(exhaust.center(), this.rotationYaw + 180)).addVector(0, 0.35 * gauge.scale(), 0);
					
					double smokeMod = (1 + Math.min(1, Math.max(0.2, Math.abs(this.getCurrentSpeed().minecraft())*2)))/2;
					
					EntitySmokeParticle sp = new EntitySmokeParticle(world, (int) (40 * (1+throttle) * smokeMod), throttle, throttle, exhaust.width());
					
					particlePos = particlePos.subtract(fakeMotion);
					
					sp.setPosition(particlePos.x, particlePos.y, particlePos.z);
					sp.setVelocity(fakeMotion.x, fakeMotion.y + 0.4 * gauge.scale(), fakeMotion.z);
					world.spawnEntity(sp);
				}
			}
			return;
		}
		
		if (this.getLiquidAmount() > 0) {
			float burnTime = BurnUtil.getBurnTime(this.getLiquid());
			if (burnTime == 0) {
				burnTime = 200; //Default to 200 for unregistered liquids
			}
			burnTime *= getDefinition().getFuelEfficiency()/100f;
			burnTime *= (Config.ConfigBalance.locoDieselFuelEfficiency / 100f);
			
			while (internalBurn < 0 && this.getLiquidAmount() > 0) {
				internalBurn += burnTime;
				theTank.drain(1, true);
			}
			
			float consumption = Math.abs(getThrottle()) + 0.05f;
			consumption *= 100;
			consumption *= gauge.scale();
			
			internalBurn -= consumption;
		}
	}
	
	@Override
	public void setDead() {
		super.setDead();
		
		if (idle != null) {
			idle.stop();
		}
		if (horn != null) {
			horn.stop();
		}
	}

	@Override
	public List<Fluid> getFluidFilter() {
		return BurnUtil.burnableFluids();
	}

	@Override
	public FluidQuantity getTankCapacity() {
		return this.getDefinition().getFuelCapacity(gauge);
	}

	@Override
	public FluidQuantity getOilTankCapacity() {
		return FluidQuantity.ZERO;
	}
}