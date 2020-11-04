package pancake;

import mindustry.gen.*;
import arc.util.*;
import pancake.world.blocks.*;
import mindustry.type.*;
import mindustry.content.*;
import mindustry.world.*;
import mindustry.mod.*;

public class SupremePancake extends Mod{
    public static Block

    // Turrets
    tileTurret;

    public SupremePancake(){
        Log.info("Loaded SupremePancake mod constructor.");
    }

    @Override // Загрузка контента
    public void loadContent(){
        tileTurret = new TileTurret("tile-turret"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 35));
            shootType = Bullets.standardCopper;
            shootSound = Sounds.shotgun;
            range = 80.0f;
            rotateSpeed = 5.0f;
            maxAmmo = 30;
            chargeTime = 30f;
            barrelsAmount = 4;
            reloadTime = 3f;
            range = 100;
            ammoUseEffect = Fx.casing1;
            health = 150;
            size = 1;
            description = "a";
        }};
    }
}
